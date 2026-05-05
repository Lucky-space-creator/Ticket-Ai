package com.ticket.aichat.rag.aggregate;

import com.ticket.aichat.manual.ManualContentRetriever;
import com.ticket.aichat.service.impl.ManualDocImpl;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在 LangChain4j 多路融合（默认 RRF）之后，再按 {@link ManualDocImpl} 剔除停用手册片段，并截断到最终条数。
 * <p>
 * 设计原因：混合检索需先「多路多拉 → RRF」，最后再统一做 doc 级治理，避免每路过早 subList 削弱融合效果。
 * 作用：最终的融合结果在做一次过滤
 */
//最终混合索引向量过滤类
public class FinalHybridContentFilter implements ContentAggregator {

    private final ContentAggregator delegate;
    private final ManualDocImpl manualDocImpl;
    /**
     * 最终条数。
     */
    private final int finalMaxResults;

    public FinalHybridContentFilter(
            ContentAggregator delegate,
            ManualDocImpl manualDocImpl,
            int finalMaxResults) {
        this.delegate = delegate;
        this.manualDocImpl = manualDocImpl;
        this.finalMaxResults = Math.max(finalMaxResults, 1);
    }

    /**
     * 混合索引向量过滤类
     */
    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        //获取已经合并重排序后的结果 ranked
        List<Content> ranked = delegate.aggregate(queryToContents);
        if (ranked.isEmpty()) {
            return ranked;
        }
        if (manualDocImpl.governanceActive()) {
            return truncate(ranked, finalMaxResults);
        }
        Set<String> disabled = manualDocImpl.getDisabledDocIds();
        List<Content> kept = ranked.stream()
                .filter(c -> ManualContentRetriever.governancePasses(c.textSegment(), disabled))
                .collect(Collectors.toCollection(ArrayList::new));
        return truncate(kept, finalMaxResults);
    }

    /**
     * 截断只获取最终条数前max条数
     * @param list 原始列表
     * @param max 最终条数
     * @return 截断后的列表
     */
    private static List<Content> truncate(List<Content> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        return new ArrayList<>(list.subList(0, max));
    }
}
