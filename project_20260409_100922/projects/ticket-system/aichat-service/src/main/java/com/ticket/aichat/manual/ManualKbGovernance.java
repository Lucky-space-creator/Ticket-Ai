package com.ticket.aichat.manual;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.aichat.mapper.KbManualDocumentMapper;
import com.ticket.entity.KbManualDocument;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 手册启停治理：停用文档不出现在 RAG 召回中。
 */
@Service
public class ManualKbGovernance {

    @Resource
    private KbManualDocumentMapper kbManualDocumentMapper;

    /** 简短缓存减轻热点查询压力 */
    private volatile Set<String> disabledCache = Collections.emptySet();
    private volatile long cacheUntilNanos = 0L;
    private static final long CACHE_TTL_NS = 10_000_000_000L; // 10s

    /** 尚无治理记录时跳过过滤（避免误判全部丢弃）。 */
    public boolean governanceActive() {
        return kbManualDocumentMapper.selectCount(null) > 0;
    }

    public Set<String> disabledDocIds() {
        long now = System.nanoTime();
        if (now < cacheUntilNanos) {
            return disabledCache;
        }
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
