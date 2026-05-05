package com.ticket.aichat.manual;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 按手册 doc_id 清理向量片段（EmbeddingStore.filter 语义由底层实现）。
 */
@Service
public class ManualVectorMaintenance {

    private static final Logger log = LoggerFactory.getLogger(ManualVectorMaintenance.class);

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    public void removeAllChunksOfDoc(String docId) {
        try {
            var filter = MetadataFilterBuilder.metadataKey(ManualKnowledgeConstants.META_DOC_ID).isEqualTo(docId);
            embeddingStore.removeAll(filter);
            log.info("已按 doc_id 删除向量切片: {}", docId);
        } catch (UnsupportedOperationException e) {
            log.warn("EmbeddingStore 不支持按条件删除，跳过 doc_id={} : {}", docId, e.getMessage());
        }
    }
}
