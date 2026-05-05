package com.ticket.aichat.mapper;

import com.ticket.aichat.enums.ManualKnowledgeEnums;
import com.ticket.aichat.rag.lexical.CalculateBM25;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 向量数据库持久层操作
 */
@Slf4j
@Service
public class VectorMaintenanceMapper {

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private CalculateBM25 calculateBM25;

    /** 对账/重灌前先删该手册全部切片 */
    public void removeAllChunksOfDoc(String docId) {
        try {
            var filter = MetadataFilterBuilder.metadataKey(ManualKnowledgeEnums.META_DOC_ID).isEqualTo(docId);
            embeddingStore.removeAll(filter);
            log.info("已按 doc_id 删除向量切片: {}", docId);
        } catch (UnsupportedOperationException e) {
            log.warn("EmbeddingStore 不支持按条件删除，跳过 doc_id={} : {}", docId, e.getMessage());
        }
        calculateBM25.removeDocument(docId);
    }
}
