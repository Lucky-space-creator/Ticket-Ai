package com.ticket.aichat.controller;

import com.ticket.entity.KnowledgeBase;
import com.ticket.aichat.service.DocumentIngestionService;
import com.ticket.aichat.service.KnowledgeBaseService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理（与单体 backend 路径一致，由网关路由至 aichat-service）
 */
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private DocumentIngestionService documentIngestionService;

    @GetMapping("/list")
    public ResponseUtil.Result<List<KnowledgeBase>> list() {
        List<KnowledgeBase> list = knowledgeBaseService.list();
        return ResponseUtil.success(list);
    }

    @GetMapping("/enabled")
    public ResponseUtil.Result<List<KnowledgeBase>> getEnabled() {
        List<KnowledgeBase> list = knowledgeBaseService.getEnabledKnowledge();
        return ResponseUtil.success(list);
    }

    @PostMapping("/add")
    public ResponseUtil.Result<KnowledgeBase> add(@RequestBody KnowledgeRequest request) {
        try {
            KnowledgeBase kb = knowledgeBaseService.addOrUpdateKnowledge(
                    request.getCategory(),
                    request.getQuestion(),
                    request.getAnswer(),
                    request.getKeywords()
            );
            return ResponseUtil.success("添加成功", kb);
        } catch (Exception e) {
            return ResponseUtil.error("添加失败: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseUtil.Result<KnowledgeBase> update(@RequestBody KnowledgeRequest request) {
        try {
            KnowledgeBase kb = knowledgeBaseService.addOrUpdateKnowledge(
                    request.getCategory(),
                    request.getQuestion(),
                    request.getAnswer(),
                    request.getKeywords()
            );
            return ResponseUtil.success("更新成功", kb);
        } catch (Exception e) {
            return ResponseUtil.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> delete(@PathVariable Long id) {
        try {
            boolean result = knowledgeBaseService.deleteKnowledge(id);
            if (result) {
                return ResponseUtil.success("删除成功");
            }
            return ResponseUtil.error("删除失败");
        } catch (Exception e) {
            return ResponseUtil.error("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/sync")
    public ResponseUtil.Result<?> sync() {
        try {
            knowledgeBaseService.syncToVectorStore();
            return ResponseUtil.success("同步成功");
        } catch (Exception e) {
            return ResponseUtil.error("同步失败: " + e.getMessage());
        }
    }

    @PostMapping("/load-files")
    public ResponseUtil.Result<?> loadFiles() {
        try {
            documentIngestionService.loadDocuments();
            return ResponseUtil.success("文件加载完成");
        } catch (Exception e) {
            return ResponseUtil.error("文件加载失败: " + e.getMessage());
        }
    }

    public static class KnowledgeRequest {
        private String category;
        private String question;
        private String answer;
        private String keywords;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public String getKeywords() {
            return keywords;
        }

        public void setKeywords(String keywords) {
            this.keywords = keywords;
        }
    }
}
