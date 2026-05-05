package com.ticket.aichat.manual;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在向量粗排之后再按启用表剔除 disabled 手册。
 */
public class ManualGovernedContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;
    private final ManualKbGovernance governance;
    private final int limitedTo;

    public ManualGovernedContentRetriever(ContentRetriever delegate, ManualKbGovernance governance, int limitedTo) {
        this.delegate = delegate;
        this.governance = governance;
        this.limitedTo = limitedTo;
    }

    @Override
    public List<Content> retrieve(Query query) {
        List<Content> wide = delegate.retrieve(query);
        if (!governance.governanceActive()) {
            return limit(wide);
        }
        Set<String> disabled = governance.disabledDocIds();
        List<Content> filtered = wide.stream()
                .filter(c -> passes(c.textSegment(), disabled))
                .collect(Collectors.toList());
        return limit(filtered);
    }

    private List<Content> limit(List<Content> list) {
        if (list.size() <= limitedTo || limitedTo <= 0) {
            return list;
        }
        return list.subList(0, limitedTo);
    }

    static boolean passes(TextSegment segment, Set<String> disabledDocIds) {
        if (segment == null) {
            return false;
        }
        if (disabledDocIds == null || disabledDocIds.isEmpty()) {
            return true;
        }
        Metadata meta = segment.metadata();
        if (meta == null) {
            return true;
        }
        String docId = meta.getString(ManualKnowledgeConstants.META_DOC_ID);
        if (docId == null || docId.isBlank()) {
            return true;
        }
        return !disabledDocIds.contains(docId);
    }
}
