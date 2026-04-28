# Kibana 查询指南 - 购票智能客服系统

## 前置条件
1. Logstash 已启动并成功采集日志（参见 `deploy/logstash/ticket-system-pipeline.conf`）
2. Elasticsearch 索引模板已加载（参见 `deploy/elasticsearch/templates/`）
3. Kibana 中已创建索引模式：
   - `ticket-system-*` （全量应用日志）
   - `ai-chat-trace-*` （AI聊天专用日志）

---

## 1. 按 traceId 查询单次完整链路

### Kibana Discover 查询（KQL）
```
traceId: "a1b2c3d4e5f6g7h8"
```

### Dev Tools (REST API)
```json
GET ai-chat-trace-*/_search
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
HAVING total_tokens > 10000  -- 阈值：单日总消耗超过10000 tokens
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

### 查看所有 AI 调用耗时分布
```json
GET ai-chat-trace-*/_search
{
  "size": 0,
  "query": {
    "match": { "logType": "ai-chat-trace" }
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

### 按任务类型分组统计耗时
```json
GET ai-chat-trace-*/_search
{
  "size": 0,
  "query": {
    "match": { "logType": "ai-chat-trace" }
  },
  "aggs": {
    "by_task_name": {
      "terms": { "field": "taskName", "size": 10 },
      "aggs": {
        "avg_time": { "avg": { "field": "totalMs" } },
        "max_time": { "max": { "field": "totalMs" } }
      }
    }
  }
}
```

---

## 4. Kibana Dashboard 配置建议

### 建议创建的可视化面板

| 面板名称 | 类型 | 用途 |
|---------|------|------|
| 日调用次数趋势 | Timestring | 监控AI调用量 |
| 平均响应时间 | Metric | 监控LLM延迟 |
| Token消耗总量 | Timestring | 成本监控 |
| 用户Token排名 | Top N | 发现异常用户 |
| 错误率 | Timestring | 服务健康度 |
| traceId 分布 | Table | 链路覆盖度 |

---

## 5. 索引管理命令

### 加载索引模板
```bash
# 全量日志模板
curl -X PUT "localhost:9200/_index_template/ticket-system" \
  -H 'Content-Type: application/json' \
  @deploy/elasticsearch/templates/ticket-system-index-template.json

# AI聊天专用模板
curl -X PUT "localhost:9200/_index_template/ai-chat-trace" \
  -H 'Content-Type: application/json' \
  @deploy/elasticsearch/templates/ai-chat-trace-index-template.json
```

### 查看已加载模板
```bash
curl -X GET "localhost:9200/_index_template/ticket-system"
curl -X GET "localhost:9200/_index_template/ai-chat-trace"
```
