# Kibana 查询指南 - 购票智能客服系统（学习版 ELK）

## 学习路线（5 步）

1. **启动 ES**：运行 `deploy/scripts/1-start-es.ps1`，浏览器访问 `https://127.0.0.1:9200`，将 `elastic` 密码设为 **`123456`**（见 `deploy/ELK-LEARN.md`）。
2. **注册索引模板**：桌面 **`启动ticket_sys服务.bat`** 会在 ES 启动后自动执行 `deploy/scripts/5-load-templates.ps1`；也可手动运行该脚本或按文末 curl 执行。
3. **启动 Logstash**：`deploy/scripts/3-start-logstash.ps1`（默认 `ES_PWD=123456`，与 `elastic` 一致）。
4. **启动 Filebeat**：`deploy/scripts/4-start-filebeat.ps1`，确认 `D:\ELK\Filebeat\Beats\9.3.3\filebeat\filebeat.yml` 中 `paths` 指向你的项目 `logs` 目录。
5. **启动 Kibana + 微服务**：`deploy/scripts/2-start-kibana.ps1`，在 Kibana 创建 **Data View**：`ticket-app-*`、`ticket-aichat-*`，打开 Discover 用 KQL 查 `traceId`。

详细说明见 [`deploy/ELK-LEARN.md`](../ELK-LEARN.md)；采集链路见 [`deploy/logstash/ticket-pipeline.conf`](../logstash/ticket-pipeline.conf)。

---

## 前置条件

1. Logstash 已加载 `ticket-pipeline.conf` 且监听 **5044**，Filebeat 已连上。
2. Elasticsearch 索引模板已加载（`ticket-app`、`ticket-aichat`）。
3. Kibana 中已创建 Data View：
   - `ticket-app-*`（各微服务主 JSON 日志）
   - `ticket-aichat-*`（`ai-chat-trace-*.json.log`）

---

## 1. 按 traceId 查询单次完整链路

### Kibana Discover 查询（KQL）

```
traceId: "a1b2c3d4e5f6g7h8"
```

### Dev Tools (REST API)

```json
GET ticket-app-*,ticket-aichat-*/_search
{
  "query": {
    "term": {
      "traceId": "a1b2c3d4e5f6g7h8"
    }
  },
  "sort": [
    { "@timestamp": "asc" }
  ]
}
```

仅查 AI 专用索引：

```json
GET ticket-aichat-*/_search
{
  "query": {
    "term": {
      "traceId": "a1b2c3d4e5f6g7h8"
    }
  },
  "sort": [
    { "@timestamp": "asc" }
  ]
}
```

---

## 2. 按用户统计 Token 消耗量

> **注意**：token 数据存储在 MySQL `chat_record` 表中，以下为 SQL 查询。
> 如需在 Kibana 中分析，需将 chat_record 同步到 ES。

### MySQL 按日统计每个用户的 token 消耗

```sql
SELECT 
    DATE(created_at) AS stat_date,
    user_id,
    COUNT(*) AS chat_count,
    SUM(input_tokens) AS total_input_tokens,
    SUM(output_tokens) AS total_output_tokens,
    ROUND(SUM(output_tokens) / NULLIF(COUNT(*), 0), 1) AS avg_output_per_chat
FROM chat_record 
WHERE msg_type = 'robot'
  AND created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY stat_date, user_id
ORDER BY stat_date DESC, total_output_tokens DESC;
```

### MySQL 检测异常高消耗用户（单日 > 阈值）

```sql
SELECT 
    user_id,
    DATE(created_at) AS stat_date,
    SUM(input_tokens) + SUM(output_tokens) AS total_tokens,
    COUNT(*) AS chat_count
FROM chat_record 
WHERE msg_type = 'robot'
  AND created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY user_id, stat_date
HAVING total_tokens > 10000
ORDER BY total_tokens DESC;
```

### MySQL 按小时分布查看 token 消耗趋势

```sql
SELECT 
    DATE_FORMAT(created_at, '%Y-%m-%d %H:00') AS hour_bucket,
    SUM(input_tokens) AS input_tokens,
    SUM(output_tokens) AS output_tokens,
    COUNT(*) AS request_count
FROM chat_record 
WHERE msg_type = 'robot'
  AND created_at >= CURDATE()
GROUP BY hour_bucket
ORDER BY hour_bucket;
```

---

## 3. Kibana AI 聊天链路追踪分析

> 以下聚合依赖日志 JSON 中是否包含 `totalMs`、`taskName` 等字段；若字段名不同，请在 Discover 中先看一条文档再改 `field` 名。

### 查看 AI 相关日志量（按 logType）

```json
GET ticket-aichat-*/_search
{
  "size": 0,
  "query": {
    "term": { "logType": "ai-chat-trace" }
  }
}
```

### 若存在数值字段 totalMs，可查看分位数

```json
GET ticket-aichat-*/_search
{
  "size": 0,
  "query": {
    "term": { "logType": "ai-chat-trace" }
  },
  "aggs": {
    "response_time_percentiles": {
      "percentiles": {
        "field": "totalMs",
        "percents": [50, 90, 95, 99]
      }
    }
  }
}
```

---

## 4. Kibana Dashboard 配置建议

| 面板名称 | 类型 | 用途 |
|---------|------|------|
| 日调用次数趋势 | Lens / TSVB | 监控写入量 |
| 平均响应时间 | Metric | 延迟（需有耗时字段） |
| Token消耗总量 | 外部 DB 或同步 ES | 成本监控 |
| 错误率 | Lens | `level: ERROR` |
| traceId 分布 | Table | 链路覆盖度 |

---

## 5. 索引管理命令

### 加载索引模板（HTTPS + 自签证书需 `-k`）

```bash
curl -k -u elastic:123456 -X PUT "https://127.0.0.1:9200/_index_template/ticket-app" \
  -H "Content-Type: application/json" \
  --data-binary "@deploy/elasticsearch/templates/ticket-app-template.json"

curl -k -u elastic:123456 -X PUT "https://127.0.0.1:9200/_index_template/ticket-aichat" \
  -H "Content-Type: application/json" \
  --data-binary "@deploy/elasticsearch/templates/ticket-aichat-template.json"
```

或在项目根目录执行 PowerShell：`deploy/scripts/5-load-templates.ps1`（可设置环境变量 `ELASTIC_PASSWORD`）。

### 查看已加载模板

```bash
curl -k -u elastic:123456 "https://127.0.0.1:9200/_index_template/ticket-app"
curl -k -u elastic:123456 "https://127.0.0.1:9200/_index_template/ticket-aichat"
```
