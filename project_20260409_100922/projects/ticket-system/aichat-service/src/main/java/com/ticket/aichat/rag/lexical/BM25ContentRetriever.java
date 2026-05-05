package com.ticket.aichat.rag.lexical;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合检索中的「稀疏」通道：对当前问题做 BM25，返回与向量同结构的 {@link Content} 列表。
 * <p>
 * 索引数据来自 {@link CalculateBM25}，与 {@link com.ticket.aichat.service.DocumentIngestionService} 写入的切片一致。
 * 作用：将当前的问题与索引数据进行匹配，返回匹配到的内容，获取topK 个结果
 */
public class BM25ContentRetriever implements ContentRetriever {

    // 索引
    private final CalculateBM25 calculateBM25;
    private final int topK;

    public BM25ContentRetriever(CalculateBM25 lexicalIndex, int topK) {
        this.calculateBM25 = lexicalIndex;
        this.topK = Math.max(topK, 1);
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (query == null || query.text() == null) {
            return List.of();
        }
        List<CalculateBM25.LexicalHit> hits = calculateBM25.rankedSearch(query.text(), topK);
        List<Content> out = new ArrayList<>(hits.size());
        for (CalculateBM25.LexicalHit h : hits) {
            if (h.chunk() != null && h.chunk().segment() != null) {
                out.add(Content.from(h.chunk().segment()));
            }
        }
        return out;
    }
}
