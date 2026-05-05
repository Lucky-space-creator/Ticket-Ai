package com.ticket.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.aichat.mapper.KbManualDocumentMapper;
import com.ticket.entity.KbManualDocument;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 手册级启停治理：enabled=0 的 doc 不参与 RAG；与数据库表 kb_manual_document 对应。
 * <p>
 * disabled 列表带短 TTL 缓存，管理端改开关后 {@link #setManualDocEnabled} 会失效缓存。
 * 目的：过滤掉停用的 doc。
 */
@Service
public class ManualDocImpl {

    @Resource
    private KbManualDocumentMapper kbManualDocumentMapper;

    /**缓存当前被禁用的文档ID集合（即 enabled = 0 的那些 docId）。**/
    private volatile Set<String> disabledCache = Collections.emptySet();
    /**缓存的有效期截止时间（纳秒级时间戳）。 初始值为 0，表示缓存已失效或尚未初始化。**/
    private volatile long cacheUntilNanos = 0L;
    /**缓存存活时间（Time To Live），单位纳秒。10_000_000_000L 纳米 = 10 秒。**/
    private static final long CACHE_TTL_NS = 10_000_000_000L; // 10s

    /** 尚无治理记录时跳过过滤（避免误判全部丢弃）。 */
    public boolean governanceActive() {
        //看看数据库中是否有数据
        return kbManualDocumentMapper.selectCount(null) <= 0;
    }

    /**
     * 获取被禁用的文档ID集合。
     *
     * @return 被禁用的文档ID集合
     */
    public Set<String> getDisabledDocIds() {
        //获取当前纳秒时间
        long now = System.nanoTime();
        if (now < cacheUntilNanos) {
            return disabledCache;
        }
        //给下面的代码块加锁
        synchronized (this) {
            now = System.nanoTime();
            if (now < cacheUntilNanos) {
                return disabledCache;
            }
            LambdaQueryWrapper<KbManualDocument> q = new LambdaQueryWrapper<>();
            q.eq(KbManualDocument::getEnabled, 0);
            disabledCache = kbManualDocumentMapper.selectList(q).stream()
                    .map(KbManualDocument::getDocId)
                    .collect(Collectors.toSet());
            cacheUntilNanos = System.nanoTime() + CACHE_TTL_NS;
            return disabledCache;
        }
    }

    // 更新 doc_id 启停状态
    public boolean setManualDocEnabled(String docId, boolean enabled) {
        KbManualDocument row = kbManualDocumentMapper.selectById(docId);
        if (row == null) {
            return false;
        }
        row.setEnabled(enabled ? 1 : 0);
        row.setUpdatedAt(LocalDateTime.now());
        kbManualDocumentMapper.updateById(row);
        cacheUntilNanos = 0L;
        return true;
    }
}
