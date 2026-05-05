package com.ticket.aichat.enums;

import com.ticket.aichat.manual.ManualContentRetriever;

/**
 * 写入 EmbeddingStore/Chroma metadata 的手册字段名常量。
 * <p>
 * RAG {@link ManualContentRetriever#retrieve} / 混合链路 {@link com.ticket.aichat.rag.aggregate.FinalHybridContentFilter}
 * 通过 {@link #META_DOC_ID} 与 kb_manual_document 对齐做启停过滤。
 */
public final class ManualKnowledgeEnums {

    /**
     * 手册文档 ID（相对路径哈希）。
     */
    public static final String META_DOC_ID = "doc_id";

    /**
     * 手册文档相对路径。
     */
    public static final String META_RELATIVE_PATH = "relative_path";

    /**
     * 手册文档内容 checksum。
     */
    public static final String META_CONTENT_CHECKSUM = "content_checksum";

    /**
     * 私有构造函数，禁止实例化。
     */
    private ManualKnowledgeEnums() {
    }
}
