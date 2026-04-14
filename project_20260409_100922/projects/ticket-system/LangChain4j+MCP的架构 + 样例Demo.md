## LangChain4j + MCP 调用业务 API 完整示例

MCP（Model Context Protocol）的核心思路是：**你的业务逻辑作为工具（Tool）暴露给 MCP 服务器，LangChain4j 客户端连接服务器，让 AI 自动调用这些工具**。

下面给出一个**可以直接运行的完整例子**。

---

### 一、整体架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   AI 服务       │────▶│   MCP 客户端    │────▶│   MCP 服务器    │
│  (你的应用)      │◀────│  (LangChain4j)  │◀────│  (业务工具)      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                          │
                                                          ▼
                                              ┌─────────────────────┐
                                              │ 业务 API / 数据库    │
                                              │ (查票、购票、退票)    │
                                              └─────────────────────┘
```

---

### 二、完整代码示例（可直接运行）

这是一个**完整的 Java 类**，使用 JBang 运行，无需任何构建配置：

```java
///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j:1.0.0-beta6
//DEPS dev.langchain4j:langchain4j-mcp:1.0.0-beta6
//DEPS dev.langchain4j:langchain4j-ollama:1.0.0-beta6
//DEPS org.slf4j:slf4j-simple:2.0.17
//JAVA 21

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 购票智能客服 - MCP 示例
 * 运行命令: jbang TicketMcpDemo.java
 */
public class TicketMcpDemo {

    static {
        // 重要：日志输出到 stderr，避免干扰 MCP 的 stdout 通信
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");
    }

    private static final Logger log = LoggerFactory.getLogger(TicketMcpDemo.class);

    // ==================== 1. 定义业务工具（购票相关 API） ====================

    /**
     * 购票服务类 - 包含所有可被 AI 调用的业务工具
     * 每个 @Tool 注解的方法都会自动暴露给 MCP 服务器
     */
    public static class TicketService {

        @Tool("查询车次信息，根据出发地、目的地和日期返回可用车次列表")
        public String searchTrains(
                @Tool.Memo("出发城市，如：北京、上海、广州") String from,
                @Tool.Memo("到达城市，如：上海、北京、深圳") String to,
                @Tool.Memo("出发日期，格式：YYYY-MM-DD") String date) {

            log.info("调用 searchTrains: from={}, to={}, date={}", from, to, date);

            // 模拟调用业务 API 返回数据
            return String.format("""
                    ✅ 查询成功！从 %s 到 %s (%s) 的车次：
                    
                    1. G101 高铁 | 08:00-12:00 | 4小时 | 一等座 ¥800 二等座 ¥500
                    2. D205 动车 | 09:30-14:30 | 5小时 | 一等座 ¥600 二等座 ¥380
                    3. K789 快车 | 10:00-18:00 | 8小时 | 硬座 ¥180 硬卧 ¥320
                    
                    如需购票，请告知车次号。
                    """, from, to, date);
        }

        @Tool("购买车票，根据车次号、乘客信息和座位类型下单")
        public String buyTicket(
                @Tool.Memo("车次号，如：G101、D205") String trainNo,
                @Tool.Memo("乘客姓名") String passengerName,
                @Tool.Memo("身份证号") String idNumber,
                @Tool.Memo("座位类型：一等座/二等座/硬座/硬卧") String seatType) {

            log.info("调用 buyTicket: trainNo={}, passenger={}, seatType={}", trainNo, passengerName, seatType);

            // 模拟调用购票 API
            String orderId = "ORD" + System.currentTimeMillis();

            return String.format("""
                    🎫 购票成功！
                    
                    订单号：%s
                    车次：%s
                    乘客：%s
                    座位：%s
                    
                    请在 30 分钟内完成支付，否则订单将自动取消。
                    """, orderId, trainNo, passengerName, seatType);
        }

        @Tool("查询订单状态，根据订单号返回订单详情")
        public String queryOrder(@Tool.Memo("订单号") String orderId) {

            log.info("调用 queryOrder: orderId={}", orderId);

            // 模拟查询订单 API
            return String.format("""
                    📋 订单 %s 详情：
                    
                    状态：待支付
                    车次：G101
                    乘客：张三
                    座位：二等座
                    金额：¥500
                    下单时间：2026-04-14 10:30:00
                    """, orderId);
        }

        @Tool("取消订单，根据订单号取消未支付的订单")
        public String cancelOrder(@Tool.Memo("订单号") String orderId) {

            log.info("调用 cancelOrder: orderId={}", orderId);

            // 模拟调用取消订单 API
            return String.format("""
                    ❌ 订单 %s 已取消。
                    
                    如有疑问，请联系客服 12306。
                    """, orderId);
        }
    }

    // ==================== 2. AI 服务接口定义 ====================

    interface CustomerServiceBot {
        String chat(String userMessage);
    }

    // ==================== 3. 主程序 ====================

    public static void main(String[] args) throws Exception {

        log.info("启动购票智能客服系统...");

        // ---------- 步骤 1：创建 MCP 服务器（内嵌模式）----------
        // 将业务工具类包装成 MCP 服务器
        TicketService ticketService = new TicketService();
        
        // 创建 MCP 服务器，通过 stdio 与客户端通信
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("java", "-cp", System.getProperty("java.class.path"),
                        "dev.langchain4j.community.mcp.server.McpServerRunner",
                        ticketService.getClass().getName()))
                .logEvents(true)
                .build();

        // 创建 MCP 客户端
        McpClient mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();

        // 创建工具提供者
        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(List.of(mcpClient))
                .build();

        // ---------- 步骤 2：配置 LLM（使用 Ollama 本地模型）----------
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen2:1.5b")
                .temperature(0.7)
                .logRequests(true)
                .logResponses(true)
                .build();

        // ---------- 步骤 3：创建 AI 服务 ----------
        CustomerServiceBot bot = AiServices.builder(CustomerServiceBot.class)
                .chatLanguageModel(model)
                .toolProvider(toolProvider)
                .build();

        // ---------- 步骤 4：测试对话 ----------
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🤖 购票智能客服已启动，请开始对话...");
        System.out.println("=".repeat(60) + "\n");

        // 测试场景 1：查询车次
        String question1 = "我想买明天从北京到上海的高铁票，帮我查一下有哪些车次？";
        System.out.println("👤 用户: " + question1);
        String response1 = bot.chat(question1);
        System.out.println("🤖 客服: " + response1);
        System.out.println("\n" + "-".repeat(60) + "\n");

        // 测试场景 2：购票
        String question2 = "帮我买 G101 次列车的一等座，乘客叫张三，身份证 11010119900307663X";
        System.out.println("👤 用户: " + question2);
        String response2 = bot.chat(question2);
        System.out.println("🤖 客服: " + response2);
        System.out.println("\n" + "-".repeat(60) + "\n");

        // 测试场景 3：查询订单
        String question3 = "查询一下我刚买的订单状态";
        System.out.println("👤 用户: " + question3);
        String response3 = bot.chat(question3);
        System.out.println("🤖 客服: " + response3);

        // 关闭客户端
        mcpClient.close();
    }
}
```
### 四、关键点说明

| 组件 | 作用 | 在你项目中的对应 |
|------|------|------------------|
| `@Tool` | 标记方法为 AI 可调用的工具 | 购票、查票、退票等业务方法 |
| `McpTransport` | 定义通信方式（stdio/HTTP） | 本地开发用 stdio，远程用 HTTP |
| `McpToolProvider` | 将 MCP 工具提供给 AI | 自动发现并注册所有 `@Tool` 方法 |
| `AiServices` | 创建 AI 服务实例 | 绑定 LLM 和工具提供者 |

**AI 如何决定调用哪个工具？**
- LLM 根据用户问题和 `@Tool` 注解中的 `description` 自动判断
- 例如用户说"查一下北京到上海的车次"，AI 会自动调用 `searchTrains` 方法

---

### 六、通信方式选择

| 场景 | 推荐方式 | 说明 |
|------|----------|------|
| 本地开发、工具与 AI 在同一进程 | **内嵌模式**（如上面示例） | 最简单，无需额外部署 |
| 工具作为独立服务运行 | **stdio** | 通过标准输入输出通信 |
| 工具在远程服务器 | **HTTP/SSE** | 跨网络调用，适合微服务架构 |

**远程 HTTP 模式示例**：
```java
McpTransport transport = new HttpMcpTransport.Builder()
    .sseUrl("http://your-server:8080/mcp/sse")
    .build();
```

---

### 七、针对你的购票系统

你已经有完整的后端业务 API（查询车次、购票、退票等），只需要：

1. 创建 `TicketTool` 类，用 `@Tool` 注解包装每个业务 API 调用
2. 启动 MCP 服务器（Quarkus 有现成的 `quarkus-mcp-server` 扩展）
3. 在现有 Spring Boot 项目中配置 MCP 客户端
4. AI 就能自动调用你的业务 API 了

这样实现后，你的客服系统就能真正"动手"帮用户查票、买票、退票，而不是只给出文字建议了。