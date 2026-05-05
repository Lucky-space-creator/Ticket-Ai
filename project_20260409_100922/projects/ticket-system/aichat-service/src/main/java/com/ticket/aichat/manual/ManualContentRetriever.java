package com.ticket.aichat.manual;

import com.ticket.aichat.enums.ManualKnowledgeEnums;
import com.ticket.aichat.rag.aggregate.FinalHybridContentFilter;
import com.ticket.aichat.service.impl.ManualDocImpl;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 向量粗排之后按治理表剔除「整本停用」手册的片段。
 * <p>
 * 「单路 RAG」：作为最外层 Decorator（含最终条数上限）。「混合检索」：治理改由 {@link FinalHybridContentFilter} 在 RRF 融合后完成。
 * 作用：用来粗排，然后按治理表过滤。过滤掉停用的手册的片段。
 */
public class ManualContentRetriever implements ContentRetriever {

    /**
     * 粗排结果。
     */
    private final ContentRetriever delegate;
    /**
     * 手动治理。
     */
    private final ManualDocImpl manualDocImpl;
    /**
     * 最终条数上限。
     */
    private final int limitedTo;

    public ManualContentRetriever(ContentRetriever delegate, ManualDocImpl governance, int limitedTo) {
        this.delegate = delegate;
        this.manualDocImpl = governance;
        this.limitedTo = limitedTo;
    }

    /**
     * 粗排之后按治理表剔除「整本停用」手册的片段。
     */
    @Override
    public List<Content> retrieve(Query query) {
        // 获取原始片段内容
        List<Content> wide = delegate.retrieve(query);
        // 看看数据库中治理表是否有数据？ 如果没有，则直接返回，并且限制条数
        if (manualDocImpl.governanceActive()) {
            return limit(wide);
        }
        Set<String> disabled = manualDocImpl.getDisabledDocIds();
        List<Content> filtered = wide.stream()
                .filter(c -> governancePasses(c.textSegment(), disabled)) // 按治理表Id过滤对应的文档片段
                .collect(Collectors.toList());
        return limit(filtered);
    }

    /**
     * 截取指定长度。
     */
    private List<Content> limit(List<Content> list) {
        if (list.size() <= limitedTo || limitedTo <= 0) {
            return list;
        }
        return list.subList(0, limitedTo);
    }

    /**
     * hybrid 流水线在 RRF 融合后复用的同一治理规则：metadata.doc_id ∈ disabled 则丢弃。
     * @param segment 文档片段
     * @param disabledDocIds 停用手册ID集合
     * @return 是否通过
     */
    public static boolean governancePasses(TextSegment segment, Set<String> disabledDocIds) {
        if (segment == null) {
            return false;
        }
        if (disabledDocIds == null || disabledDocIds.isEmpty()) {
            return true;
        }
        Metadata meta = segment.metadata();
        // 没有元数据
        if (meta == null) {
            return true;
        }
        String docId = meta.getString(ManualKnowledgeEnums.META_DOC_ID);
        if (docId == null || docId.isBlank()) {
            return true;
        }
        return !disabledDocIds.contains(docId);
    }
}
