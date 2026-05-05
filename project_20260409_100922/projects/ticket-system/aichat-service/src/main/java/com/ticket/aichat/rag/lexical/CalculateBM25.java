package com.ticket.aichat.rag.lexical;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 手册切片的内存 BM25 倒排索引，与向量库使用同一套分片结果（{@code doc_id#chunkIndex}）。
 * <p>
 * 入库时由 {@link com.ticket.aichat.service.DocumentIngestionService} 同步；按 doc 删向量时必须 {@link #removeDocument}。
 *
 * BM25 概率检索模型
 */
@Component
public class CalculateBM25 {

    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;

    /** chunkKey -> 倒排文档项 */
    private final Map<String, LexicalChunk> chunkByKey = new HashMap<>();

    /** 词项 -> (chunkKey -> 该 chunk 内该词出现次数) */
    private final Map<String, Map<String, Integer>> postings = new HashMap<>();

    private final Object mutateLock = new Object();

    /**
     * 全量重建向量前清空稀疏索引，防止残留幽灵片段。
     */
    public void clearAll() {
        synchronized (mutateLock) {
            chunkByKey.clear();
            postings.clear();
        }
    }

    /**
     * 删除某本手册下的全部切片倒排（与按 doc_id 删向量配套调用）。
     */
    public void removeDocument(String docId) {
        if (docId == null || docId.isBlank()) {
            return;
        }
        synchronized (mutateLock) {
            List<String> toRemove = new ArrayList<>();
            for (LexicalChunk c : chunkByKey.values()) {
                if (Objects.equals(docId, c.docId())) {
                    toRemove.add(c.key());
                }
            }
            removeChunkKeysUnsafe(toRemove);
        }
    }

    /**
     * 用当前分片列表整体替换该 doc 的稀疏索引（增量对账/重灌时调用）。
     */
    public void replaceDocument(String docId, List<TextSegment> segments) {
        if (docId == null || docId.isBlank() || segments == null || segments.isEmpty()) {
            return;
        }
        synchronized (mutateLock) {
            removeDocumentUnsafe(docId);
            for (int i = 0; i < segments.size(); i++) {
                TextSegment seg = segments.get(i);
                String text = seg == null ? "" : seg.text();
                String chunkKey = docId + "#" + i;
                List<String> toks = tokens(text);
                LexicalChunk lc = new LexicalChunk(chunkKey, docId, seg, toks);
                chunkByKey.put(chunkKey, lc);
                Map<String, Integer> termFreqInChunk = freqMap(toks);
                for (Map.Entry<String, Integer> fe : termFreqInChunk.entrySet()) {
                    postings.computeIfAbsent(fe.getKey(), __ -> new HashMap<>()).put(chunkKey, fe.getValue());
                }
            }
        }
    }

    /**
     * BM25 排序，高分在前；仅返回前 {@code topK} 条。
     */
    public List<LexicalHit> rankedSearch(String rawQuery, int topK) {
        if (rawQuery == null || rawQuery.isBlank() || topK <= 0) {
            return List.of();
        }
        List<String> queryTerms = tokens(rawQuery);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        synchronized (mutateLock) {
            if (chunkByKey.isEmpty()) {
                return List.of();
            }
            int corpusChunkCount = chunkByKey.size();
            long totalTerms = 0;
            for (LexicalChunk c : chunkByKey.values()) {
                totalTerms += c.tokens().size();
            }
            double avgLen = (double) totalTerms / corpusChunkCount;

            Map<String, Double> scores = new HashMap<>();
            for (String qt : freqMap(queryTerms).keySet()) {
                Map<String, Integer> plist = postings.get(qt);
                if (plist == null || plist.isEmpty()) {
                    continue;
                }
                int df = plist.size();
                double idf = idfBm25(corpusChunkCount, df);

                for (Map.Entry<String, Integer> posting : plist.entrySet()) {
                    String chunkKey = posting.getKey();
                    LexicalChunk chunk = chunkByKey.get(chunkKey);
                    if (chunk == null) {
                        continue;
                    }
                    int tf = freqInTerms(chunk.tokens(), qt);
                    double denom = tf + BM25_K1 * (1 - BM25_B + BM25_B * chunk.tokens().size() / Math.max(avgLen, 1e-9));
                    double contrib = idf * (tf * (BM25_K1 + 1)) / Math.max(denom, 1e-9);
                    scores.merge(chunkKey, contrib, Double::sum);
                }
            }

            return scores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(topK)
                    .map(e -> new LexicalHit(chunkByKey.get(e.getKey()), e.getValue()))
                    .filter(h -> h.chunk() != null && h.chunk().segment() != null)
                    .toList();
        }
    }

    private static int freqInTerms(List<String> haystackTerms, String term) {
        int n = 0;
        for (String t : haystackTerms) {
            if (t.equals(term)) {
                n++;
            }
        }
        return n;
    }

    private static double idfBm25(int nChunks, int df) {
        if (df <= 0) {
            return 0;
        }
        return Math.log(1 + (nChunks - df + 0.5) / (df + 0.5));
    }

    private void removeDocumentUnsafe(String docId) {
        List<String> keys = new ArrayList<>();
        for (LexicalChunk c : chunkByKey.values()) {
            if (Objects.equals(docId, c.docId())) {
                keys.add(c.key());
            }
        }
        removeChunkKeysUnsafe(keys);
    }

    private void removeChunkKeysUnsafe(Collection<String> chunkKeys) {
        for (String key : chunkKeys) {
            LexicalChunk rm = chunkByKey.remove(key);
            if (rm == null) {
                continue;
            }
            Map<String, Integer> termFreqInChunk = freqMap(rm.tokens());
            for (String term : termFreqInChunk.keySet()) {
                Map<String, Integer> pmap = postings.get(term);
                if (pmap != null) {
                    pmap.remove(key);
                    if (pmap.isEmpty()) {
                        postings.remove(term);
                    }
                }
            }
        }
    }

    private static Map<String, Integer> freqMap(List<String> terms) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String t : terms) {
            if (!t.isEmpty()) {
                m.merge(t, 1, Integer::sum);
            }
        }
        return m;
    }

    private static List<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String norm = text.toLowerCase(Locale.ROOT);
        List<String> raw = splitWords(norm);
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            String t = r.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() == 1 && !Character.isIdeographic(r.charAt(0))) {
                continue;
            }
            if (t.length() > 128) {
                t = t.substring(0, 128);
            }
            out.add(t);
        }
        return out.isEmpty() ? List.of(norm) : out;
    }

    /** 中英混排：优先按区域分词，失败则退化为单字/数字提取 */
    private static List<String> splitWords(String text) {
        BreakIterator bi = BreakIterator.getWordInstance(Locale.CHINA);
        bi.setText(text);
        List<String> chunks = new ArrayList<>();
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            String w = text.substring(start, end);
            if (!w.trim().isEmpty()) {
                chunks.add(w.trim());
            }
        }
        if (chunks.isEmpty()) {
            for (char c : text.toCharArray()) {
                if (Character.isLetterOrDigit(c) || Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                    chunks.add(String.valueOf(Character.toLowerCase(c)));
                }
            }
        }
        return chunks;
    }

    /** 稀疏通道中的一条带分词的切片 */
    public record LexicalChunk(String key, String docId, TextSegment segment, List<String> tokens) {
    }

    /** BM25 得分，用于观测或调试 */
    public record LexicalHit(LexicalChunk chunk, double score) {
    }
}
