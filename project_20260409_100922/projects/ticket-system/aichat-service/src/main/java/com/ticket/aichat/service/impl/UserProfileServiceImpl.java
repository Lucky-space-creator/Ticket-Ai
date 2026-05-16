package com.ticket.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.aichat.mapper.ChatRecordMapper;
import com.ticket.aichat.mapper.UserChatProfileMapper;
import com.ticket.aichat.service.UserProfileService;
import com.ticket.dto.mq.UserProfileGenerateEvent;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.UserChatProfile;
import com.ticket.service.RocketMQProducerService;
import com.ticket.util.MQIdempotentUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 用户画像服务实现：基于历史聊天记录生成用户偏好摘要。
 * <p>
 * 存储：MySQL（user_chat_profile 表） + Redis 缓存。
 * 生成：通过 RocketMQ 异步触发 LLM 总结。
 */
@Slf4j
@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final String PROFILE_CACHE_KEY = "user:profile:";
    private static final String DEFAULT_PROFILE = "";

    /**
     * 用户级锁：防止同一用户的画像被并发生成（缓存击穿保护 + 生成互斥）
     */
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private UserChatProfileMapper userChatProfileMapper;

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Value("${user-profile.enabled:true}")
    private boolean enabled;

    @Value("${user-profile.history-limit:50}")
    private int historyLimit;

    @Value("${user-profile.cache-ttl-hours:24}")
    private int cacheTtlHours;

    @Getter
    @Value("${user-profile.message-threshold:20}")
    private int messageThreshold;

    @Override
    public String getUserProfile(Long userId) {
        if (!enabled || userId == null) {
            return DEFAULT_PROFILE;
        }

        // 1. Redis 缓存
        String cached = getFromCache(userId);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，加用户级锁防止缓存击穿
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        lock.lock();
        try {
            cached = getFromCache(userId);
            if (cached != null) {
                return cached;
            }
            String profile = getFromMySQL(userId);
            putToCache(userId, profile);
            return profile;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 发送 MQ 异步触发画像生成（不阻塞调用线程）
     */
    @Override
    public void generateProfile(Long userId, String sessionId) {
        if (!enabled || userId == null) {
            return;
        }
        UserProfileGenerateEvent event = new UserProfileGenerateEvent();
        event.setMessageId(idempotentUtil.generateMessageId());
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setTriggerSource("session_end");
        event.setTimestamp(System.currentTimeMillis());
        rocketMQProducerService.sendUserProfileGenerateEvent(event);
        log.info("用户画像生成事件已发送: userId={}", userId);
    }

    /**
     * 实际执行画像生成（由 MQ Consumer 调用）
     */
    public void doGenerateProfile(Long userId, String sessionId) {
        if (!enabled || userId == null) {
            return;
        }

        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.info("用户画像正在生成中，跳过: userId={}", userId);
            return;
        }

        try {
            log.info("开始生成用户画像: userId={}", userId);

            List<ChatRecord> records = queryRecentRecords(userId);
            if (records.isEmpty()) {
                log.info("用户无聊天记录，跳过画像生成: userId={}", userId);
                return;
            }

            String summary = summarizeByLLM(records);
            if (summary == null || summary.isBlank()) {
                log.warn("LLM 画像总结为空: userId={}", userId);
                return;
            }

            upsertProfile(userId, summary, records.size());
            putToCache(userId, summary);

            log.info("用户画像生成完成: userId={}, length={}", userId, summary.length());
        } catch (Exception e) {
            log.error("用户画像生成失败: userId={}", userId, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除用户画像
     * @param userId 用户 ID
     */
    @Override
    public void deleteProfile(Long userId) {
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<UserChatProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserChatProfile::getUserId, userId);
        userChatProfileMapper.delete(wrapper);
        stringRedisTemplate.delete(PROFILE_CACHE_KEY + userId);
        log.info("用户画像已删除: userId={}", userId);
    }

    // ==================== private methods ====================

    private String getFromCache(Long userId) {
        try {
            return stringRedisTemplate.opsForValue().get(PROFILE_CACHE_KEY + userId);
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败: userId={}", userId);
            return null;
        }
    }

    private void putToCache(Long userId, String profile) {
        try {
            String value = (profile != null && !profile.isBlank()) ? profile : DEFAULT_PROFILE;
            stringRedisTemplate.opsForValue().set(PROFILE_CACHE_KEY + userId, value, Duration.ofHours(cacheTtlHours));
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败: userId={}", userId);
        }
    }

    private String getFromMySQL(Long userId) {
        try {
            LambdaQueryWrapper<UserChatProfile> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserChatProfile::getUserId, userId).last("LIMIT 1");
            UserChatProfile profile = userChatProfileMapper.selectOne(wrapper);
            return profile != null ? profile.getSummary() : DEFAULT_PROFILE;
        } catch (Exception e) {
            log.warn("MySQL 用户画像查询失败: userId={}", userId);
            return DEFAULT_PROFILE;
        }
    }

    private void upsertProfile(Long userId, String summary, int messageCount) {
        LambdaQueryWrapper<UserChatProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserChatProfile::getUserId, userId);
        UserChatProfile existing = userChatProfileMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setSummary(summary);
            existing.setMessageCount(messageCount);
            userChatProfileMapper.updateById(existing);
        } else {
            UserChatProfile newProfile = new UserChatProfile();
            newProfile.setUserId(userId);
            newProfile.setSummary(summary);
            newProfile.setMessageCount(messageCount);
            userChatProfileMapper.insert(newProfile);
        }
    }

    private List<ChatRecord> queryRecentRecords(Long userId) {
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getUserId, userId)
                .in(ChatRecord::getMsgType, "user", "robot")
                .orderByDesc(ChatRecord::getCreatedAt)
                .last("LIMIT " + historyLimit);
        List<ChatRecord> records = chatRecordMapper.selectList(wrapper);
        java.util.Collections.reverse(records);
        return records;
    }

    private String summarizeByLLM(List<ChatRecord> records) {
        StringBuilder conversation = new StringBuilder();
        for (ChatRecord record : records) {
            String role = "user".equals(record.getMsgType()) ? "用户" : "客服";
            conversation.append(role).append("：").append(record.getMessage()).append("\n");
        }

        String systemPrompt = """
                你是一个用户画像分析助手。请根据以下用户与客服的对话记录，总结用户的画像信息。
                要求：
                1. 总结用户的常问业务类型（如退票、改签、查车次、购票等）
                2. 提取用户偏好（如座位类型、常用路线、出行习惯等）
                3. 记录用户的关键特征（如沟通风格、问题复杂度等）
                4. 如果对话中没有明显特征，简要记录"暂无明显偏好特征"
                5. 总结控制在300字以内，用简洁的条目式描述
                6. 不要编造对话中没有的信息
                """;

        String userPrompt = "以下是用户的历史对话记录：\n\n" + conversation;

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userPrompt));
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            return response.content().text();
        } catch (Exception e) {
            log.error("LLM 画像总结调用失败", e);
            return null;
        }
    }
}
