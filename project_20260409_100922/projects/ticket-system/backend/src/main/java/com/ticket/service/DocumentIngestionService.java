package com.ticket.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 文档加载服务
 * 负责加载知识库文档到向量数据库
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Value("${knowledge.base-path}")
    private String knowledgeBasePath;

    @Value("${knowledge.init-on-startup}")
    private boolean initOnStartup;

    @Value("${ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.embedding-model}")
    private String ollamaEmbeddingModelName;

    @PostConstruct
    public void ingestDocuments() {
        if (!initOnStartup) {
            log.info("知识库初始化已禁用（knowledge.init-on-startup=false）");
            return;
        }

        // 异步加载知识库，避免阻塞应用启动
        CompletableFuture.runAsync(() -> {
            try {
                // 等待5秒，让应用完全启动
                TimeUnit.SECONDS.sleep(5);
                loadDocuments();
            } catch (Exception e) {
                log.error("知识库加载失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 加载知识库目录中的所有文档到向量数据库
     * 可用于API手动触发文件加载
     */
    public void loadDocuments() {
        log.info("=== 开始加载知识库 ===");

        // 先测试 embedding 模型连接，最多重试3次
        int maxRetries = 3;
        int retryCount = 0;
        long waitSeconds = 30;

        while (retryCount < maxRetries) {
            if (testEmbeddingModel()) {
                break; // 连接成功，继续执行
            }
            
            retryCount++;
            if (retryCount >= maxRetries) {
                log.error("Embedding 模型连接失败，已重试 {} 次，跳过知识库加载。请检查 Ollama 服务是否运行。", maxRetries);
                log.error("Ollama 服务地址: {}，请确认服务已启动且可访问。", ollamaBaseUrl);
                return;
            }
            
            log.warn("Embedding 模型连接失败，第 {} 次重试，等待 {} 秒后重试", retryCount, waitSeconds);
            try {
                TimeUnit.SECONDS.sleep(waitSeconds);
                // 每次重试等待时间加倍
                waitSeconds *= 2;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        try {
            // 清除现有向量库，避免重复累积
            try {
                embeddingStore.removeAll();
                log.info("已清空向量数据库中的旧数据");
            } catch (Exception e) {
                log.warn("清空向量数据库失败: {}", e.getMessage());
                // 继续执行，因为可能向量库本来就为空或清空操作不被支持
            }
            
            Path path = Paths.get(knowledgeBasePath);
            File dir = path.toFile();
            log.debug("知识库目录绝对路径: {}", dir.getAbsolutePath());

            if (!dir.exists()) {
                log.info("知识库目录不存在，创建目录: {}", knowledgeBasePath);
                boolean mkdir = dir.mkdirs();

                if (!mkdir) {
                    log.error("创建目录失败，请检查权限");
                    return;
                }

                createSampleDocument();
                return;
            }

            // 获取目录下所有文件
            File[] allFiles = dir.listFiles();
            if (allFiles != null) {
                log.debug("目录下所有文件 ({} 个):", allFiles.length);
                for (File f : allFiles) {
                    log.debug("  - {}", f.getName());
                }
            }
            File[] files = dir.listFiles((d, name) -> 
                name.toLowerCase().endsWith(".txt") || name.toLowerCase().endsWith(".md")
            );
            
            if (files != null) {
                log.debug("过滤后文件 ({} 个):", files.length);
                for (File f : files) {
                    log.debug("  - {}", f.getName());
                }
            }
            
            if (files == null || files.length == 0) {
                log.info("知识库目录中没有找到文档");
                createSampleDocument();
                return;
            }
            
            List<Document> documents = new ArrayList<>();
            for (File file : files) {
                try {
                    Document document = FileSystemDocumentLoader.loadDocument(file.toPath(), new TextDocumentParser());
                    documents.add(document);
                    log.info("加载文档: {}", file.getName());
                } catch (Exception e) {
                    log.warn("加载文档失败: {} - {}", file.getName(), e.getMessage(), e);
                }
            }

            log.info("找到 {} 个文档", documents.size());

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(1000, 100))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();

            ingestor.ingest(documents);
            log.info("知识库加载完成！");

        } catch (Exception e) {
            log.error("知识库加载失败: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private boolean testEmbeddingModel() {
        try {
            log.info("测试 Embedding 模型连接...");
            log.info("Ollama 服务地址: {}", ollamaBaseUrl);
            log.info("嵌入模型名称: {}", ollamaEmbeddingModelName);
            // 使用一个小文本测试 embedding
            embeddingModel.embed("test");
            log.info("Embedding 模型连接成功");
            return true;
        } catch (Exception e) {
            log.warn("Embedding 模型连接失败: {} (异常类型: {})", e.getMessage(), e.getClass().getSimpleName());
            log.warn("请检查 Ollama 服务是否运行，配置地址: {}", ollamaBaseUrl);
            log.warn("嵌入模型名称: {}，请确认模型已下载（使用命令: ollama pull {}）", ollamaEmbeddingModelName, ollamaEmbeddingModelName);
            log.warn("如果使用远程服务，请确认配置文件中的 ai.ollama.base-url 设置正确");
            log.warn("错误详情:", e);
            return false;
        }
    }

    private void createSampleDocument() {
        log.info("创建示例知识库文档...");
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
            
            【改签规则】
            1. 开车前48小时以上，可改签预售期内的任意车次
            2. 开车前不足48小时，可改签开车前及开车后至票面日期当日24:00之间的车次
            3. 改签只能办理一次
            
            【乘车规定】
            1. 成人可免费携带一名身高不足1.2米的儿童
            2. 学生票享受硬座半价优惠
            3. 军人、残疾人等特殊群体享受优先购票服务
            """;

        addDocument(sampleContent, "ticket_rules.txt");
    }

    public void addDocument(String content, String source) {
        // 1. 保存到文件系统
        saveToFileSystem(content, source);
        
        // 2. 添加到向量数据库
        Document document = Document.from(content,
                new dev.langchain4j.data.document.Metadata().put("source", source));

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(document);
        log.info("文档已添加到向量数据库: {}", source);
    }
    
    private void saveToFileSystem(String content, String filename) {
        try {
            Path knowledgeDir = Paths.get(knowledgeBasePath);
            if (!knowledgeDir.toFile().exists()) {
                knowledgeDir.toFile().mkdirs();
            }
            
            // 确保文件名安全，移除路径分隔符
            String safeFilename = Paths.get(filename).getFileName().toString();
            Path filePath = knowledgeDir.resolve(safeFilename);
            
            java.nio.file.Files.writeString(filePath, content, java.nio.charset.StandardCharsets.UTF_8);
            log.info("文档已保存到文件: {}", filePath);
        } catch (Exception e) {
            log.warn("无法保存文档到文件系统: {}", e.getMessage());
            // 不抛出异常，继续添加到向量数据库
        }
    }
}