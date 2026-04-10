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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档加载服务
 * 负责加载知识库文档到向量数据库
 */
@Service
public class DocumentIngestionService {

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Value("${knowledge.base-path:./knowledge-base}")
    private String knowledgeBasePath;

    @PostConstruct
    public void ingestDocuments() {
        System.out.println("=== 开始加载知识库 ===");

        try {
            Path path = Paths.get(knowledgeBasePath);
            File dir = path.toFile();

            if (!dir.exists()) {
                System.out.println("知识库目录不存在，创建目录: " + knowledgeBasePath);
                dir.mkdirs();
                createSampleDocument();
                return;
            }

            List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                    path,
                    new TextDocumentParser()
            );

            if (documents.isEmpty()) {
                System.out.println("知识库目录中没有找到文档");
                createSampleDocument();
                return;
            }

            System.out.println("找到 " + documents.size() + " 个文档");

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 100))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();

            ingestor.ingest(documents);
            System.out.println("知识库加载完成！");

        } catch (Exception e) {
            System.err.println("知识库加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createSampleDocument() {
        System.out.println("创建示例知识库文档...");
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
        Document document = Document.from(content,
                new dev.langchain4j.data.document.Metadata().put("source", source));

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(document);
        System.out.println("文档已添加: " + source);
    }
}