package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.KnowledgeBase;
import com.ticket.mapper.KnowledgeBaseMapper;
import com.ticket.service.AIChatService;
import com.ticket.service.KnowledgeBaseService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private AIChatService aiChatService;


    @Override
    public List<KnowledgeBase> getEnabledKnowledge() {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getStatus, 1);
        return list(wrapper);
    }

    @Override
    public void syncToVectorStore() {
        log.info("开始同步知识库到向量数据库...");

        List<KnowledgeBase> knowledgeList = getEnabledKnowledge();

        // 清空现有向量库，避免重复累积
        try {
            embeddingStore.removeAll();
            log.info("已清空向量数据库中的旧数据");
        } catch (Exception e) {
            log.warn("清空向量数据库失败: {}", e.getMessage());
            // 继续执行，因为可能向量库本来就为空或清空操作不被支持
        }

        if (knowledgeList.isEmpty()) {
            log.warn("知识库为空，跳过同步");
            return;
        }

        // 1. 将知识库转换为文本片段
        List<TextSegment> segments = knowledgeList.stream()
                .map(kb -> {
                    String content = String.format("问题：%s\n答案：%s\n关键词：%s",
                            kb.getQuestion(),
                            kb.getAnswer(),
                            kb.getKeywords() != null ? kb.getKeywords() : "");
                    // 可以添加元数据，方便后续溯源
                    return TextSegment.from(content);
                })
                .collect(Collectors.toList());

        // 2. 将文本片段转换为向量（需要传入参数）
        Response<List<Embedding>> embeddings = embeddingModel.embedAll(segments);
        List<Embedding> content = embeddings.content();

        // 3. 调用addAll方法（传入embeddings和segments）
        try {
            List<String> ids = embeddingStore.addAll(content, segments);
            log.info("知识库同步完成，共 {} 条知识，生成 {} 个向量ID", knowledgeList.size(), ids.size());
        } catch (Exception e) {
            log.info("向量数据库集合尚未创建，跳过知识库同步（将在文档加载后自动创建）");
            // 不抛出异常，避免影响应用启动，DocumentIngestionService后续会创建集合
        }
    }

    @Override
    public KnowledgeBase addOrUpdateKnowledge(String category, String question, String answer, String keywords) {
        // 检查是否已存在相同问题
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getQuestion, question);
        KnowledgeBase existing = getOne(wrapper);

        KnowledgeBase kb;
        if (existing != null) {
            // 更新
            existing.setCategory(category);
            existing.setAnswer(answer);
            existing.setKeywords(keywords);
            existing.setStatus(1);
            updateById(existing);
            kb = existing;
        } else {
            // 新增
            kb = new KnowledgeBase();
            kb.setCategory(category);
            kb.setQuestion(question);
            kb.setAnswer(answer);
            kb.setKeywords(keywords);
            kb.setStatus(1);
            kb.setHitCount(0);
            save(kb);
        }

        // 同步到向量库
        syncToVectorStore();

        return kb;
    }

    @Override
    public boolean deleteKnowledge(Long id) {
        boolean result = removeById(id);
        if (result) {
            // 重新同步（简化处理，实际生产可用增量更新）
            syncToVectorStore();
        }
        return result;
    }
}
