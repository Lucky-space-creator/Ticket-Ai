# AI智能客服与RAG系统

## 概述
AI智能客服系统基于RAG（检索增强生成）架构，结合了LangChain4j框架、ChromaDB向量数据库和多种AI模型（Ollama本地模型、OpenAI API）。系统能够理解自然语言查询，检索相关知识，并调用业务工具执行购票、查询等操作。

## 系统架构
### RAG六步流程
```
用户输入 → 向量检索 → 上下文增强 → AI生成 → 工具调用 → 流式响应
    ↓         ↓           ↓         ↓         ↓         ↓
自然语言  知识库检索  相关文档注入  大语言模型  业务工具  实时输出
```

### 核心组件
1. **AI模型配置** - 支持多模型切换（Ollama/OpenAI/HTTP-API）
2. **向量数据库** - ChromaDB存储知识库文档向量
3. **聊天记忆** - MessageWindowChatMemory维护对话上下文
4. **AI工具** - 业务功能暴露为AI可调用工具
5. **知识库管理** - 文档加载、向量化、检索
6. **降级容错** - 多级降级策略保证服务可用性

## 配置管理
### application.yml配置
```yaml
ai:
  model:
    provider: "ollama"  # ollama, openai, http-api
    ollama:
      base-url: "http://localhost:11434"
      model-name: "llama3.2:3b"
      temperature: 0.7
      timeout-seconds: 60
    openai:
      api-key: "${OPENAI_API_KEY}"
      model-name: "gpt-3.5-turbo"
    http-api:
      base-url: "http://ai-api.example.com"
      model-name: "custom-model"
  
  rag:
    enabled: true
    knowledge-base-path: "classpath:knowledge-base/"
    top-k: 3  # 检索返回的文档数量
    min-score: 0.6  # 相似度最低分数阈值
  
  tools:
    enabled: true
    max-consecutive-tool-calls: 5
  
  memory:
    max-messages: 20  # 对话记忆最大消息数
```

## 核心实现
### 1. AI模型配置（多模型支持）
```java
@Configuration
public class AIModelConfig {
    
    @Bean
    @ConditionalOnProperty(name = "ai.model.provider", havingValue = "ollama")
    public ChatLanguageModel ollamaChatModel(
            @Value("${ai.model.ollama.base-url}") String baseUrl,
            @Value("${ai.model.ollama.model-name}") String modelName,
            @Value("${ai.model.ollama.temperature}") Double temperature) {
        
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.model.provider", havingValue = "openai")
    public ChatLanguageModel openAiChatModel(
            @Value("${ai.model.openai.api-key}") String apiKey,
            @Value("${ai.model.openai.model-name}") String modelName) {
        
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.model.provider", havingValue = "http-api")
    public ChatLanguageModel httpApiChatModel(
            @Value("${ai.model.http-api.base-url}") String baseUrl,
            @Value("${ai.model.http-api.model-name}") String modelName) {
        
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("dummy")  // 如果不需要认证
                .modelName(modelName)
                .build();
    }
}
```

### 2. RAG配置与降级策略
```java
@Configuration
@Slf4j
public class RAGConfig {
    
    @Bean
    @ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
    public Retriever<TextSegment> knowledgeBaseRetriever(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            @Value("${ai.rag.top-k}") int topK,
            @Value("${ai.rag.min-score}") double minScore) {
        
        return EmbeddingStoreRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(topK)
                .minScore(minScore)
                .build();
    }
    
    @Bean
    @ConditionalOnMissingBean(Retriever.class)
    public Retriever<TextSegment> emptyRetriever() {
        // RAG禁用时的降级方案：返回空检索器
        log.warn("RAG is disabled, using empty retriever");
        return query -> List.of();
    }
    
    @Bean
    public ChatMemory chatMemory(@Value("${ai.memory.max-messages}") int maxMessages) {
        // 基于消息窗口的对话记忆，每个用户独立
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .id(MemoryId::from)  // 从请求中提取用户ID
                .build();
    }
}
```

### 3. AI业务工具定义
```java
@Component
public class AIBusinessTool {
    
    private final TrainService trainService;
    private final OrderService orderService;
    private final PassengerService passengerService;
    
    public AIBusinessTool(TrainService trainService, OrderService orderService, 
                         PassengerService passengerService) {
        this.trainService = trainService;
        this.orderService = orderService;
        this.passengerService = passengerService;
    }
    
    @Tool("查询车次信息")
    public List<TrainVO> searchTrains(
            @P("出发站") String departureStation,
            @P("到达站") String arrivalStation,
            @P("出发日期，格式：yyyy-MM-dd") String departureDate) {
        
        return trainService.searchTrains(departureStation, arrivalStation, departureDate);
    }
    
    @Tool("查询余票")
    public List<TrainSeatInventoryVO> queryTicketInventory(
            @P("车次ID") Long trainId,
            @P("出发日期，格式：yyyy-MM-dd") String departureDate) {
        
        return trainService.getSeatInventory(trainId, departureDate);
    }
    
    @Tool("创建订单")
    public OrderCreateResult createOrder(
            @P("车次ID") Long trainId,
            @P("出发日期，格式：yyyy-MM-dd") String departureDate,
            @P("席别类型") String seatType,
            @P("乘客ID列表，用逗号分隔") String passengerIds) {
        
        OrderCreateRequest request = new OrderCreateRequest();
        request.setTrainId(trainId);
        request.setDepartureDate(departureDate);
        request.setSeatType(seatType);
        
        List<Long> ids = Arrays.stream(passengerIds.split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        request.setPassengerIds(ids);
        
        return orderService.createOrder(getCurrentUserId(), request);
    }
    
    @Tool("查询我的订单")
    public List<OrderVO> getMyOrders(
            @P("订单状态，可选值：PENDING-待支付，PAID-已支付，CANCELLED-已取消，REFUNDED-已退票，COMPLETED-已完成") 
            String status) {
        
        return orderService.getUserOrders(getCurrentUserId(), status);
    }
    
    @Tool("退票")
    public RefundResult refundOrder(
            @P("订单号") String orderNo,
            @P("退票原因") String reason) {
        
        return orderService.refundOrder(orderNo, reason);
    }
    
    @Tool("查询我的常用乘客")
    public List<PassengerVO> getMyPassengers() {
        return passengerService.getPassengers(getCurrentUserId());
    }
    
    @Tool("添加常用乘客")
    public PassengerVO addPassenger(
            @P("乘客姓名") String name,
            @P("身份证号") String idCard,
            @P("手机号") String phone) {
        
        PassengerCreateRequest request = new PassengerCreateRequest();
        request.setName(name);
        request.setIdCard(idCard);
        request.setPhone(phone);
        request.setPassengerType("ADULT");
        
        return passengerService.addPassenger(getCurrentUserId(), request);
    }
    
    private Long getCurrentUserId() {
        // 从安全上下文获取当前用户ID
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            // 假设UserDetails中有getUserId方法
            return ((CustomUserDetails) userDetails).getUserId();
        }
        throw new BusinessException("AUTH_001", "用户未登录");
    }
}
```

### 4. AI对话服务核心
```java
@Service
@Slf4j
public class AIChatServiceImpl implements AIChatService {
    
    private final ChatLanguageModel chatModel;
    private final Retriever<TextSegment> retriever;
    private final ChatMemory chatMemory;
    private final List<Object> tools;
    
    public AIChatServiceImpl(ChatLanguageModel chatModel, Retriever<TextSegment> retriever,
                            ChatMemory chatMemory, List<Object> tools) {
        this.chatModel = chatModel;
        this.retriever = retriever;
        this.chatMemory = chatMemory;
        this.tools = tools;
    }
    
    @Override
    public String chat(String userId, String userMessage) {
        try {
            // 1. 检索相关知识
            List<TextSegment> relevantDocs = retriever.retrieve(userMessage);
            
            // 2. 构建系统提示（注入检索到的知识）
            String systemPrompt = buildSystemPrompt(relevantDocs);
            
            // 3. 创建AI服务
            AiServices<ChatService> aiServices = AiServices.builder(ChatService.class)
                    .chatLanguageModel(chatModel)
                    .chatMemory(chatMemory)
                    .tools(tools)
                    .systemPromptProvider(ignore -> systemPrompt)
                    .build();
            
            // 4. 执行对话
            ChatService chatService = aiServices.create();
            return chatService.chat(userId, userMessage);
            
        } catch (Exception e) {
            log.error("AI对话失败", e);
            return "抱歉，AI服务暂时不可用，请稍后重试。";
        }
    }
    
    @Override
    public Flux<String> chatStream(String userId, String userMessage) {
        return Flux.create(sink -> {
            try {
                // 1. 检索相关知识
                List<TextSegment> relevantDocs = retriever.retrieve(userMessage);
                
                // 2. 构建系统提示
                String systemPrompt = buildSystemPrompt(relevantDocs);
                
                // 3. 创建流式AI服务
                StreamChatModel streamModel = getStreamChatModel();
                AiServices<StreamChatService> aiServices = AiServices.builder(StreamChatService.class)
                        .streamChatLanguageModel(streamModel)
                        .chatMemory(chatMemory)
                        .tools(tools)
                        .systemPromptProvider(ignore -> systemPrompt)
                        .build();
                
                // 4. 流式响应
                StreamChatService chatService = aiServices.create();
                chatService.chatStream(userId, userMessage)
                        .subscribe(
                                chunk -> sink.next(chunk),
                                error -> {
                                    log.error("AI流式对话失败", error);
                                    sink.next("抱歉，AI服务出现错误。");
                                    sink.complete();
                                },
                                sink::complete
                        );
                
            } catch (Exception e) {
                log.error("AI流式对话初始化失败", e);
                sink.next("抱歉，AI服务暂时不可用。");
                sink.complete();
            }
        });
    }
    
    private String buildSystemPrompt(List<TextSegment> relevantDocs) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个12306购票系统的智能客服助手。请根据以下知识库信息和用户的问题，提供准确、有帮助的回答。\n\n");
        
        if (!relevantDocs.isEmpty()) {
            prompt.append("【相关知识】\n");
            for (TextSegment doc : relevantDocs) {
                prompt.append(doc.text()).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("【系统能力】\n");
        prompt.append("1. 你可以查询车次、余票信息\n");
        prompt.append("2. 你可以帮用户创建订单、查询订单、退票\n");
        prompt.append("3. 你可以管理常用乘客信息\n");
        prompt.append("4. 你可以回答关于购票流程、退改签政策等问题\n\n");
        
        prompt.append("【回答要求】\n");
        prompt.append("1. 回答要简洁、准确、友好\n");
        prompt.append("2. 如果用户的问题涉及具体操作，询问必要的参数\n");
        prompt.append("3. 如果不知道答案，如实告知并建议联系人工客服\n");
        prompt.append("4. 对于购票相关操作，确保用户已登录\n");
        
        return prompt.toString();
    }
    
    private StreamChatModel getStreamChatModel() {
        // 根据配置返回流式模型
        if (chatModel instanceof OllamaChatModel) {
            return (OllamaChatModel) chatModel;
        } else if (chatModel instanceof OpenAiChatModel) {
            return (OpenAiChatModel) chatModel;
        }
        throw new UnsupportedOperationException("当前模型不支持流式响应");
    }
    
    interface ChatService {
        String chat(@MemoryId String userId, @UserMessage String message);
    }
    
    interface StreamChatService {
        Flux<String> chatStream(@MemoryId String userId, @UserMessage String message);
    }
}
```

### 5. 知识库初始化与同步
```java
@Component
@Slf4j
public class KnowledgeBaseInitializer implements ApplicationRunner {
    
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentIngestionService documentIngestionService;
    
    public KnowledgeBaseInitializer(EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   DocumentIngestionService documentIngestionService) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentIngestionService = documentIngestionService;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始初始化知识库...");
        
        try {
            // 1. 从文件系统加载文档
            List<Document> documents = documentIngestionService.loadDocuments();
            
            if (documents.isEmpty()) {
                log.warn("未找到知识库文档，跳过初始化");
                return;
            }
            
            // 2. 文本分割
            List<TextSegment> segments = splitDocuments(documents);
            
            // 3. 生成向量
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            
            // 4. 存储到向量数据库
            embeddingStore.addAll(embeddings, segments);
            
            log.info("知识库初始化完成，共加载 {} 个文档，分割为 {} 个片段", 
                    documents.size(), segments.size());
            
        } catch (Exception e) {
            log.error("知识库初始化失败", e);
        }
    }
    
    private List<TextSegment> splitDocuments(List<Document> documents) {
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);
        return splitter.splitAll(documents);
    }
}
```

## API接口
### 1. 普通AI对话
**请求地址**: `POST /api/ai/chat`

**请求参数**:
```json
{
  "message": "帮我查一下明天北京到上海的高铁",
  "sessionId": "用户会话ID（可选）"
}
```

**响应**:
```json
{
  "success": true,
  "code": "200",
  "message": "成功",
  "data": {
    "response": "已为您查询到明天北京到上海的高铁车次：G123、G125...",
    "sessionId": "session_123456",
    "timestamp": "2026-04-20 14:30:25"
  }
}
```

### 2. 流式AI对话（SSE）
**请求地址**: `GET /api/ai/chat/stream`

**查询参数**:
- `message` - 用户消息
- `sessionId` - 会话ID（可选）

**响应格式**: Server-Sent Events (SSE)
```
data: {"content": "已", "type": "chunk"}

data: {"content": "为", "type": "chunk"}

data: {"content": "您", "type": "chunk"}

data: {"content": "查询", "type": "chunk"}

data: {"content": "到...", "type": "complete"}
```

## 使用场景示例
### 场景1：车次查询
```
用户：帮我查一下4月25号从北京到广州的高铁
AI：已为您查询到4月25日北京到广州的高铁车次：
     1. G67，08:00-16:30，一等座余票45张，二等座余票120张
     2. G69，10:30-19:00，一等座余票32张，二等座余票98张
     您需要预订哪个车次？
```

### 场景2：购票流程
```
用户：我想订两张明天北京到上海的一等座
AI：请提供乘客的身份证号和手机号
用户：乘客是张三，身份证110101199001011234，手机13800138000
AI：已为您创建订单，订单号20260420123456789，请及时支付
```

### 场景3：订单查询
```
用户：查一下我的待支付订单
AI：您有1个待支付订单：
     订单号：20260420123456789
     车次：G123，北京南-上海虹桥，4月20日 08:00
     金额：1106元，支付截止时间：14:30
```

### 场景4：问题解答
```
用户：退票手续费怎么算？
AI：根据铁路规定：
     1. 发车前48小时以上：扣除5%手续费
     2. 发车前24-48小时：扣除10%手续费
     3. 发车前2-24小时：扣除20%手续费
     4. 发车前2小时内：不可退票
```

## 降级与容错
### 1. RAG降级策略
- **一级**: 正常RAG检索增强
- **二级**: RAG检索失败时，使用静态知识提示
- **三级**: AI模型不可用时，返回预设FAQ答案

### 2. 工具调用容错
- 工具调用异常时，捕获并返回友好错误信息
- 限制连续工具调用次数，防止无限循环
- 工具调用前验证用户权限和参数有效性

### 3. 服务健康检查
```java
@RestController
public class AIHealthController {
    
    @GetMapping("/api/ai/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("ragEnabled", true);
        health.put("modelProvider", "ollama");
        health.put("knowledgeBaseSize", getKnowledgeBaseSize());
        health.put("timestamp", new Date());
        return health;
    }
}
```

## 性能优化
### 1. 向量检索优化
- 使用HNSW索引加速相似度搜索
- 缓存频繁查询的向量结果
- 批量处理文档向量化

### 2. 模型推理优化
- 使用量化模型减少内存占用
- 模型预热，减少首次响应延迟
- 响应流式输出，提升用户体验

### 3. 内存管理
- 限制对话历史长度，防止内存泄漏
- 定期清理过期会话
- 监控AI服务内存使用

## 监控与日志
### 关键指标
1. **响应时间**: AI对话平均响应时间
2. **检索质量**: 检索结果的相关性评分
3. **工具调用成功率**: 业务工具调用成功比例
4. **用户满意度**: 用户反馈评分（预留）

### 日志记录
```java
@Aspect
@Component
@Slf4j
public class AIChatLogAspect {
    
    @Around("execution(* com.ticket.service.ai.AIChatService.chat(..))")
    public Object logChat(ProceedingJoinPoint joinPoint) throws Throwable {
        String userId = (String) joinPoint.getArgs()[0];
        String message = (String) joinPoint.getArgs()[1];
        
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("AI对话完成 - 用户: {}, 消息: {}, 耗时: {}ms", 
                    userId, message, duration);
            return result;
            
        } catch (Exception e) {
            log.error("AI对话失败 - 用户: {}, 消息: {}", userId, message, e);
            throw e;
        }
    }
}
```

## 扩展与定制
### 1. 添加新工具
```java
@Tool("新功能描述")
public ReturnType newToolMethod(@P("参数描述") String parameter) {
    // 实现业务逻辑
    return result;
}
```

### 2. 自定义知识源
```java
@Component
public class CustomKnowledgeSource {
    
    public List<Document> loadFromDatabase() {
        // 从数据库加载知识
        return knowledgeBaseMapper.selectAll()
                .stream()
                .map(kb -> Document.from(kb.getContent()))
                .collect(Collectors.toList());
    }
    
    public List<Document> loadFromAPI() {
        // 从外部API加载知识
        return externalService.fetchKnowledge()
                .stream()
                .map(Document::from)
                .collect(Collectors.toList());
    }
}
```

### 3. 多语言支持
```java
@Configuration
public class MultiLanguageConfig {
    
    @Bean
    @ConditionalOnProperty(name = "ai.language", havingValue = "en")
    public SystemPromptProvider englishPromptProvider() {
        return context -> "You are a 12306 ticket booking assistant...";
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.language", havingValue = "zh")
    public SystemPromptProvider chinesePromptProvider() {
        return context -> "你是12306购票系统的智能客服助手...";
    }
}
```