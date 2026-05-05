package com.ticket.aichat.service;

import com.ticket.entity.KnowledgeBase;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 启用 FAQ DB 字面/关键词匹配，命中则跳过 LLM（与 handbook 向量解耦）。
 */
@Service
public class QuickFaqMatchService {

    @Value("${faq.match.enabled:true}")
    private boolean enabled;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 尝试命中 FAQ；返回空 Optional 表示走 RAG。
     */
    public Optional<KnowledgeBase> tryHit(String userQuestion) {
        if (!enabled || userQuestion == null || userQuestion.isBlank()) {
            return Optional.empty();
        }
        String normalized = squash(userQuestion);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (KnowledgeBase kb : knowledgeBaseService.getEnabledKnowledge()) {
            if (kb == null || kb.getQuestion() == null || kb.getAnswer() == null) {
                continue;
            }
            String q = squash(kb.getQuestion());
            if (q.isEmpty()) {
                continue;
            }
            if (normalized.contains(q) || q.contains(normalized) && normalized.length() >= 4) {
                return Optional.of(kb);
            }
            if (keywordsHit(normalized, kb.getKeywords())) {
                return Optional.of(kb);
            }
        }
        return Optional.empty();
    }

    private static boolean keywordsHit(String normalizedUser, String keywordsCsv) {
        if (keywordsCsv == null || keywordsCsv.isBlank()) {
            return false;
        }
        for (String raw : keywordsCsv.split("[,，;；\\s]+")) {
            String k = squash(raw);
            if (k.length() >= 2 && normalizedUser.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }
}
