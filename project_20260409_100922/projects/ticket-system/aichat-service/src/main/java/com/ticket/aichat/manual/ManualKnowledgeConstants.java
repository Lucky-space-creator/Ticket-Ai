package com.ticket.aichat.manual;

/** 向量元数据中与手册治理相关的字段名（与 EmbeddingStore/Chroma metadata 对齐）。 */
public final class ManualKnowledgeConstants {

    public static final String META_DOC_ID = "doc_id";

    public static final String META_RELATIVE_PATH = "relative_path";

    public static final String META_CONTENT_CHECKSUM = "content_checksum";

    private ManualKnowledgeConstants() {
    }
}
