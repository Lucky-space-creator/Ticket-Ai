package com.ticket.aichat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.aichat.enums.ManualKnowledgeEnums;
import com.ticket.aichat.mapper.KbManualDocumentMapper;
import com.ticket.aichat.mapper.VectorMaintenanceMapper;
import com.ticket.aichat.rag.lexical.CalculateBM25;
import com.ticket.entity.KbManualDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 手册向量与稀疏（BM25）索引：增量对账、全量重建；metadata 含 doc_id 供治理与混合检索对齐。
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    // 分块向量大小
    private static final int SPLIT_CHUNK = 1000;
    // 分块重叠大小
    private static final int SPLIT_OVERLAP = 100;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    // 向量治理
    @Resource
    private VectorMaintenanceMapper vectorMaintenanceMapper;

    @Resource
    private KbManualDocumentMapper kbManualDocumentMapper;

    /** 与向量同 chunk 的内存 BM25，供 hybrid 稀疏通道检索 */
    @Resource
    private CalculateBM25 calculateBM25;

    @Value("${knowledge.base-path:knowledge-base}")
    private String knowledgeBasePath;

    /** none | reconcile_once | full_rebuild */
    @Value("${knowledge.startup-sync-mode:none}")
    private String startupSyncMode;

    @Value("${knowledge.init-on-startup:false}")
    private boolean legacyInitOnStartup;

    /**
     * 启动后异步执行手册同步：NONE 不写库；否则对账一次或全量重建。
     */
    @PostConstruct
    public void scheduleStartupIngest() {
        StartupMode mode = StartupMode.fromYaml(startupSyncMode, legacyInitOnStartup);
        if (mode == StartupMode.NONE) {
            log.info("手册向量启动同步策略: NONE");
            return;
        }
        log.info("手册向量启动同步策略: {}", mode);
        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
                if (mode == StartupMode.RECONCILE_ONCE) {
                    reconcileManualDocuments();
                } else if (mode == StartupMode.FULL_REBUILD) {
                    fullRebuildManualDocuments();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("启动手册同步线程被中断");
            } catch (Exception e) {
                log.error("启动手册向量任务失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 按文件校验增量：变更文件会先删 doc_id 下全部向量再重嵌入。
     */
    public void reconcileManualDocuments() {
        log.info("=== 手册向量对账开始 ===");
        if (!retryUntilEmbeddingReachable()) {
            log.warn("Embedding 不可用，跳过手册对账");
            return;
        }
        Path root = resolveKnowledgeRoot();
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            log.error("无法创建知识库目录 {}", root, e);
            return;
        }
        File[] files = listManualFiles(root);
        if (files == null || files.length == 0) {
            log.info("未发现 .md/.txt，写入示例后对账一次");
            createSampleDocumentPlain();
            files = listManualFiles(root);
            if (files == null || files.length == 0) {
                log.warn("仍无手册文件，结束对账");
                return;
            }
        }
        for (File f : files) {
            try {
                reconcileOneFile(root, f.toPath());
            } catch (Exception e) {
                log.error("手册对账失败 file={}: {}", f.getName(), e.getMessage(), e);
            }
        }
        log.info("手册向量对账结束（{} 文件）", files.length);
    }

    /**
     * 清空向量与治理表后再全目录重灌（慎用）。
     */
    public synchronized void fullRebuildManualDocuments() {
        log.warn("=== 手册向量全量重建（removeAll + 清空治理表）===");
        if (!retryUntilEmbeddingReachable()) {
            log.warn("Embedding 不可用，跳过全量重建");
            return;
        }
        try {
            embeddingStore.removeAll();
        } catch (Exception e) {
            log.warn("removeAll 失败，继续重建: {}", e.getMessage());
        }
        kbManualDocumentMapper.delete(new LambdaQueryWrapper<>());
        calculateBM25.clearAll();
        Path root = resolveKnowledgeRoot();
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            log.error("无法创建 {}", root, e);
            return;
        }
        File[] files = listManualFiles(root);
        if (files == null || files.length == 0) {
            createSampleDocumentPlain();
            files = listManualFiles(root);
        }
        if (files != null) {
            for (File f : files) {
                try {
                    forceIngest(root, f.toPath());
                } catch (Exception e) {
                    log.error("全量入库失败 {}", f.getName(), e);
                }
            }
        }
        log.info("手册向量全量重建完成");
    }

    /** 运维「加载文档」语义：等价全量重建 */
    public void loadDocuments() {
        fullRebuildManualDocuments();
    }

    public void addDocument(String content, String sourceFilename) throws IOException {
        saveToFileSystem(content, sourceFilename);
        Path root = resolveKnowledgeRoot();
        String posixName = Paths.get(sourceFilename).getFileName().toString().replace('\\', '/');
        Path file = root.resolve(posixName);
        try {
            forceIngest(root, file);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        log.info("文档已添加到向量数据库: {}", sourceFilename);
    }

    /**
     * 合并磁盘手册与治理库（管理端一览）。
     */
    public Collection<ManualKbItemVo> mergedManualKbView() throws IOException {
        Path root = resolveKnowledgeRoot();
        Map<String, ManualKbItemVo> map = new HashMap<>();
        if (Files.exists(root)) {
            /*
              root文件存在的话，则遍历该目录下的所有文件
             */
            try (var walk = Files.walk(root)) {
                // 过滤掉非文件和非txt/md文件
                walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".txt") || n.endsWith(".md");
                        })
                        //遍历文件，并且计算文件的sha256，并将结果保存在map中
                        .forEach(p -> {
                            String rel = posixRelativize(root, p);
                            String docId = stableDocId(rel);
                            String diskSha = null;
                            try {
                                diskSha = sha256Hex(p);
                            } catch (Exception ignored) {
                            }
                            ManualKbItemVo vo = new ManualKbItemVo();
                            vo.setDocId(docId);
                            vo.setRelativePath(rel);
                            vo.setOnDisk(true);
                            vo.setDiskChecksum(diskSha);
                            vo.setNeedsReconcile(true);
                            map.put(docId, vo);
                        });
            }
        }
        // 从数据库中获取所有手册
        List<KbManualDocument> persisted = kbManualDocumentMapper.selectList(null);
        for (KbManualDocument row : persisted) {
            //如果手册不存在，则创建一个新的手册
            ManualKbItemVo vo = map.computeIfAbsent(row.getDocId(), id -> {
                ManualKbItemVo v = new ManualKbItemVo();
                v.setDocId(id);
                v.setOnDisk(false);
                return v;
            });
            vo.setRelativePath(row.getRelativePath());
            vo.setGovernanceEnabled(intToBool(row.getEnabled()));
            vo.setIngestedChecksum(row.getContentChecksum());
            vo.setGovernanceUpdatedAt(row.getUpdatedAt());
        }
        //遍历手册，设置needsReconcile，如果手册不存在，则needsReconcile为false
        for (ManualKbItemVo vo : map.values()) {
            if (!vo.isOnDisk()) {
                vo.setNeedsReconcile(false);
            } else if (vo.getDiskChecksum() == null) {
                vo.setNeedsReconcile(true);
            } else {
                vo.setNeedsReconcile(!Objects.equals(vo.getDiskChecksum(), vo.getIngestedChecksum()));
            }
        }
        List<ManualKbItemVo> sorted = new ArrayList<>(map.values());
        //为什么要排序：磁盘文件排序和数据库文件排序不一致，所以需要排序
        sorted.sort(Comparator.comparing(ManualKbItemVo::getRelativePath, Comparator.nullsLast(String::compareTo)));
        return sorted;
    }

    private static Boolean intToBool(Integer v) {
        return v != null && v != 0;
    }

    // 工具方法：获取知识库根目录
    private Path resolveKnowledgeRoot() {
        return Paths.get(knowledgeBasePath).toAbsolutePath().normalize();
    }

    private File[] listManualFiles(Path root) {
        File dir = root.toFile();
        return dir.listFiles((d, name) -> {
            String n = name.toLowerCase(Locale.ROOT);
            return n.endsWith(".txt") || n.endsWith(".md");
        });
    }

    // 工具方法：仅一次对账
    private void reconcileOneFile(Path rootDir, Path file) throws IOException, NoSuchAlgorithmException {
        String relative = posixRelativize(rootDir, file);
        String checksum = sha256Hex(file);
        String docId = stableDocId(relative);

        upsertGovernanceSkeleton(docId, relative);

        KbManualDocument persisted = kbManualDocumentMapper.selectById(docId);
        if (persisted != null && checksum.equals(persisted.getContentChecksum())) {
            log.debug("手册未变更，跳过嵌入: {}", relative);
            return;
        }

        vectorMaintenanceMapper.removeAllChunksOfDoc(docId);
        ingestSingleDocument(rootDir, file, docId, relative, checksum);

        persisted = kbManualDocumentMapper.selectById(docId);
        if (persisted != null) {
            persisted.setContentChecksum(checksum);
            persisted.setRelativePath(relative);
            kbManualDocumentMapper.updateById(persisted);
        }
    }

    // 工具方法：强制嵌入
    private void forceIngest(Path rootDir, Path file) throws IOException, NoSuchAlgorithmException {
        String relative = posixRelativize(rootDir, file);
        String checksum = sha256Hex(file);
        String docId = stableDocId(relative);

        upsertGovernanceSkeleton(docId, relative);
        //删除旧向量,添加新向量
        vectorMaintenanceMapper.removeAllChunksOfDoc(docId);
        ingestSingleDocument(rootDir, file, docId, relative, checksum);
        KbManualDocument persisted = kbManualDocumentMapper.selectById(docId);
        if (persisted != null) {
            persisted.setContentChecksum(checksum);
            persisted.setRelativePath(relative);
            kbManualDocumentMapper.updateById(persisted);
        }
    }

    /**
     * 单文件入库：与向量使用相同 recursive 分片 → 写 Chroma + 同步 BM25 索引。
     */
    private void ingestSingleDocument(Path rootIgnored, Path file, String docId, String relative, String checksum) {
        Document loaded = FileSystemDocumentLoader.loadDocument(file, new TextDocumentParser());
        Metadata meta = mergedMetadata(loaded.metadata(), docId, relative, checksum);
        Document toIngest = Document.document(loaded.text(), meta);

        // 将文档分片
        var splitter = DocumentSplitters.recursive(SPLIT_CHUNK, SPLIT_OVERLAP);
        List<TextSegment> segments = splitter.split(toIngest);
        if (segments.isEmpty()) {
            log.warn("手册无分段内容，跳过: {}", relative);
            return;
        }
        calculateBM25.replaceDocument(docId, segments);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        log.info("嵌入手册: {} (chunks={})", relative, segments.size());
    }

    // 工具方法：合并元数据,把docId,relative,checksum添加到元数据中
    private static Metadata mergedMetadata(Metadata base, String docId, String relative, String checksum) {
        Metadata m = base != null ? base.copy() : new Metadata();
        m.put(ManualKnowledgeEnums.META_DOC_ID, docId);
        m.put(ManualKnowledgeEnums.META_RELATIVE_PATH, relative);
        m.put(ManualKnowledgeEnums.META_CONTENT_CHECKSUM, checksum);
        return m;
    }

    // 工具方法：更新手册治理
    private void upsertGovernanceSkeleton(String docId, String relative) {
        KbManualDocument existing = kbManualDocumentMapper.selectById(docId);
        if (existing == null) {
            KbManualDocument row = new KbManualDocument();
            row.setDocId(docId);
            row.setRelativePath(relative);
            row.setEnabled(1);
            row.setUpdatedAt(LocalDateTime.now());
            kbManualDocumentMapper.insert(row);
        } else if (relative != null && !relative.equals(existing.getRelativePath())) {
            existing.setRelativePath(relative);
            existing.setUpdatedAt(LocalDateTime.now());
            kbManualDocumentMapper.updateById(existing);
        }
    }

    public static String stableDocId(String relativePathPosix) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(relativePathPosix.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String posixRelativize(Path root, Path file) {
        return root.normalize().relativize(file.normalize()).toString().replace('\\', '/');
    }

    // 工具方法：计算文件 SHA256
    private static String sha256Hex(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(d);
    }

    // 工具方法：重试直到嵌入, 直到成功, 最多尝试 3 次
    private boolean retryUntilEmbeddingReachable() {
        int maxRetries = 3;
        long waitSeconds = 10;
        for (int i = 0; i < maxRetries; i++) {
            if (testEmbeddingModel()) {
                return true;
            }
            log.warn("Embedding 不可用，{}s 后重试 ({}/{})", waitSeconds, i + 1, maxRetries);
            try {
                TimeUnit.SECONDS.sleep(waitSeconds);
                waitSeconds = Math.min(waitSeconds * 2, 120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean testEmbeddingModel() {
        try {
            embeddingModel.embed("ping");
            return true;
        } catch (Exception e) {
            log.warn("Embedding 探测失败 {}", e.toString());
            return false;
        }
    }

    private void createSampleDocumentPlain() {
        String sampleContent = """
                === 12306 购票系统常见问题 ===

                【购票规则】
                1. 火车票预售期为15天
                2. 每个用户最多可购买5张车票
                3. 购票需要使用有效身份证件

                【退票规则】
                1. 开车前8天以上退票，不收取退票费
                2. 开车前48小时以上退票，收取票价5%退票费
                3. 开车前24-48小时退票，收取票价10%退票费
                4. 开车前不足24小时退票，收取票价20%退票费
                """;
        try {
            saveToFileSystem(sampleContent, "ticket_rules.txt");
        } catch (IOException e) {
            log.error("写入示例失败 {}", e.getMessage(), e);
        }
    }

    private void saveToFileSystem(String content, String filename) throws IOException {
        Path knowledgeDir = resolveKnowledgeRoot();
        Files.createDirectories(knowledgeDir);
        String safe = Paths.get(filename).getFileName().toString();
        Path fp = knowledgeDir.resolve(safe);
        Files.writeString(fp, content, StandardCharsets.UTF_8);
        log.info("文档已写入: {}", fp);
    }

    /** 运维 DTO —— 磁盘 + 治理表合并视图（供 Admin）。 */
    @Data
    public static class ManualKbItemVo {
        // 知识库文档 ID
        private String docId;
        // 相对路径
        private String relativePath;
        // 磁盘存在时使用；无库记录时为 null
        private boolean onDisk;
        /** 治理表存在时使用；磁盘-only 且无库记录时为 null */
        private Boolean governanceEnabled;
        // 磁盘文件校验和
        private String diskChecksum;
        // 嵌入库文件校验和
        private String ingestedChecksum;
        // 需要对账时为 true
        private boolean needsReconcile;
        // 治理表更新时间
        private LocalDateTime governanceUpdatedAt;
    }

    private enum StartupMode {
        NONE, // 无
        RECONCILE_ONCE, // 仅一次
        FULL_REBUILD; // 全 rebuild

        /**
         * 从 YAML 配置中解析启动模式。
         * @param rawMode YAML 配置中的启动模式字符串
         * @param legacyInit 是否为旧版初始化
         * @return 启动模式
         */
        static StartupMode fromYaml(String rawMode, boolean legacyInit) {
            if (rawMode != null) {
                // 去除前后空白，转为小写
                String m = rawMode.trim().toLowerCase(Locale.ROOT);
                if (!m.isEmpty()) {
                    return switch (m) {
                        case "none", "off", "disabled", "false" -> NONE;
                        case "reconcile_once", "reconcile-once", "incremental" -> RECONCILE_ONCE;
                        case "full_rebuild", "full-rebuild", "reload-all", "reload" -> FULL_REBUILD;
                        default -> legacyInit ? RECONCILE_ONCE : NONE;
                    };
                }
            }
            return legacyInit ? RECONCILE_ONCE : NONE;
        }
    }
}
