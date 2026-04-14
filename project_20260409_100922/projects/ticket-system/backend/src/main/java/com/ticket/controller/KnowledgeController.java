package com.ticket.controller;

import com.ticket.entity.KnowledgeBase;
import com.ticket.service.KnowledgeBaseService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理控制器
 */
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;


    /**
     * 获取所有知识库
     */
    @GetMapping("/list")
    public ResponseUtil.Result<List<KnowledgeBase>> list() {
        List<KnowledgeBase> list = knowledgeBaseService.list();
        return ResponseUtil.success(list);
    }

    /**
     * 获取启用的知识库
     */
    @GetMapping("/enabled")
    public ResponseUtil.Result<List<KnowledgeBase>> getEnabled() {
        List<KnowledgeBase> list = knowledgeBaseService.getEnabledKnowledge();
        return ResponseUtil.success(list);
    }

    /**
     * 添加知识
     */
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

    /**
     * 更新知识
     */
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

    /**
     * 删除知识
     */
    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> delete(@PathVariable Long id) {
        try {
            boolean result = knowledgeBaseService.deleteKnowledge(id);
            if (result) {
                return ResponseUtil.success("删除成功");
            } else {
                return ResponseUtil.error("删除失败");
            }
        } catch (Exception e) {
            return ResponseUtil.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 同步到向量数据库
     */
    @PostMapping("/sync")
    public ResponseUtil.Result<?> sync() {
        try {
            knowledgeBaseService.syncToVectorStore();
            return ResponseUtil.success("同步成功");
        } catch (Exception e) {
            return ResponseUtil.error("同步失败: " + e.getMessage());
        }
    }

    /**
     * 知识请求 DTO
     */
    @lombok.Data
    public static class KnowledgeRequest {
        private String category;
        private String question;
        private String answer;
        private String keywords;
    }
}
