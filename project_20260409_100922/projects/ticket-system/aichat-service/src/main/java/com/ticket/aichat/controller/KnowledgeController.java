package com.ticket.aichat.controller;

import com.ticket.aichat.service.DocumentIngestionService;
import com.ticket.aichat.service.KnowledgeBaseService;
import com.ticket.aichat.service.impl.ManualDocImpl;
import com.ticket.entity.KnowledgeBase;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

/**
 * FAQ 管理（CRUD DB）与 MD 手册运维：对账/全量入库、磁盘与治理库合并视图、手册启停用。
 */
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private DocumentIngestionService documentIngestionService;

    @Resource
    private ManualDocImpl manualDocImpl;

    /** FAQ 全量列表（含禁用） */
    @GetMapping("/list")
    public ResponseUtil.Result<java.util.List<KnowledgeBase>> list() {
        return ResponseUtil.success(knowledgeBaseService.list());
    }

    @GetMapping("/enabled")
    public ResponseUtil.Result<java.util.List<KnowledgeBase>> getEnabled() {
        return ResponseUtil.success(knowledgeBaseService.getEnabledKnowledge());
    }

    @PostMapping("/add")
    public ResponseUtil.Result<KnowledgeBase> add(@RequestBody KnowledgeRequest request) {
        try {
            KnowledgeBase kb = knowledgeBaseService.addOrUpdateKnowledge(
                    request.getCategory(),
                    request.getQuestion(),
                    request.getAnswer(),
                    request.getKeywords());
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
                    request.getKeywords());
            return ResponseUtil.success("更新成功", kb);
        } catch (Exception e) {
            return ResponseUtil.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> delete(@PathVariable Long id) {
        try {
            boolean result = knowledgeBaseService.deleteKnowledge(id);
            return result ? ResponseUtil.success("删除成功") : ResponseUtil.error("删除失败");
        } catch (Exception e) {
            return ResponseUtil.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 手册增量对账（按文件 checksum）。
     */
    @PostMapping("/sync")
    public ResponseUtil.Result<?> syncManualIncremental() {
        try {
            documentIngestionService.reconcileManualDocuments();
            return ResponseUtil.success("手册对账任务已触发");
        } catch (Exception e) {
            return ResponseUtil.error("手册对账失败: " + e.getMessage());
        }
    }

    @PostMapping("/manual/reconcile")
    public ResponseUtil.Result<?> manualReconcileExplicit() {
        return syncManualIncremental();
    }

    /**
     * 全量清空向量集合与治理表后重灌磁盘手册（慎用）。
     */
    @PostMapping("/load-files")
    public ResponseUtil.Result<?> loadFilesFullRebuild() {
        try {
            documentIngestionService.fullRebuildManualDocuments();
            return ResponseUtil.success("手册全量重建已触发");
        } catch (Exception e) {
            return ResponseUtil.error("文件加载失败: " + e.getMessage());
        }
    }

    /** 磁盘手册 + kb_manual_document 合并列表，供运维判断是否需要 reconcile */
    @GetMapping("/manual-docs")
    public ResponseUtil.Result<Collection<DocumentIngestionService.ManualKbItemVo>> manualDocs() {
        try {
            return ResponseUtil.success(documentIngestionService.mergedManualKbView());
        } catch (Exception e) {
            return ResponseUtil.error("获取手册清单失败: " + e.getMessage());
        }
    }

    /**
     * 按 doc_id（相对路径哈希）启停手册；停用后向量与 BM25 命中会在 RAG 侧被剔除。
     */
    @PutMapping("/manual-docs/{docId}/enabled")
    public ResponseUtil.Result<?> manualDocToggle(
            @PathVariable("docId") String docId,
            @RequestBody EnabledToggle body) {
        if (body == null || body.enabled == null) {
            return ResponseUtil.error("请求体缺少 enabled");
        }
        // 更新向量与 BM25 索引
        boolean ok = manualDocImpl.setManualDocEnabled(docId, Boolean.TRUE.equals(body.enabled));
        return ok ? ResponseUtil.success("更新成功") : ResponseUtil.error("未见该手册治理记录（请先完成对账入库）");
    }

    @Getter
    @Setter
    public static class KnowledgeRequest {
        private String category;
        private String question;
        private String answer;
        private String keywords;

    }

    @Getter
    @Setter
    public static class EnabledToggle {
        private Boolean enabled;
    }
}
