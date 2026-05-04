# ELK 学习版（本机 D:\ELK + 项目 deploy）

## 账号约定（统一）

| 用户 | 密码 | 说明 |
|------|------|------|
| `elastic` | `123456` | ES 超级用户；Kibana 进程连接 ES、Logstash、`5-load-templates.ps1` 均使用此账号 |

**首次或重装 ES 后**，在 `D:\ELK\ElasticSearch\elasticsearch-9.3.3\bin` 执行：

```bat
elasticsearch-reset-password -u elastic -i
```

将密码设为 **`123456`**（与 `D:\ELK\Kibana\kibana-9.3.3\config\kibana.yml`、`3-start-logstash.ps1`、`5-load-templates.ps1` 一致）。若使用其他密码，请同步修改上述文件，或设置环境变量 `ES_PWD`、`ELASTIC_PASSWORD`。

> 说明：学习版让 Kibana 使用 `elastic` 连接 ES，避免单独维护 `kibana_system`。生产环境请改为专用内置用户或服务账号。

## elastic 登录不上（最常见原因）

配置文件里的 **`123456` 只是约定**；若你从未执行 `elasticsearch-reset-password`，或安装时自动生成了别的密码，**集群里真实密码与文档不一致**，就会登录失败。

### 1. 确认访问方式

- 地址必须是 **`https://127.0.0.1:9200`**（或 `https://localhost:9200`），**不要用 `http://`**。
- 浏览器会提示自签证书不安全，需选择「继续访问」（开发环境可忽略）。

### 2. 重置 `elastic` 密码（推荐）

1. **先停掉 Elasticsearch**（任务管理器结束占用 **9200** 的进程，或运行桌面「关闭」脚本里对应步骤）。
2. 打开 **cmd**，执行：

```bat
cd /d D:\ELK\ElasticSearch\elasticsearch-9.3.3\bin
elasticsearch-reset-password -u elastic -i
```

3. 按提示输入新密码；若要与当前工程一致，请输入 **`123456`**。
4. **重新启动 ES**，再用浏览器或下方 curl 验证。

### 3. 用 curl 快速验证（本机）

把 `你的密码` 换成上一步设好的密码：

```bat
curl -k -u elastic:你的密码 https://127.0.0.1:9200/
```

若返回 JSON 且含 `cluster_name`，说明账号密码正确。

### 4. 改密码后必须同步的位置

若新密码**不是** `123456`，请把下面所有位置的密码改成同一个：

| 位置 |
|------|
| `D:\ELK\Kibana\kibana-9.3.3\config\kibana.yml` → `elasticsearch.password` |
| 桌面 `启动ticket_sys服务.bat` → `set ES_PWD=...` 与 `set ELASTIC_PASSWORD=...` |
| `deploy/scripts/3-start-logstash.ps1` 默认 `ES_PWD`，或启动前设置环境变量 |
| `deploy/scripts/5-load-templates.ps1` 默认密码，或设置 `ELASTIC_PASSWORD` |

然后**重启 Kibana、Logstash**，再试。

## 若 ES 因「集群状态」无法启动

`elasticsearch.yml` 已改为 `discovery.type: single-node`。若仍报错，可**备份后删除** `D:\ELK\ElasticSearch\elasticsearch-9.3.3\data` 再启动（会清空本机 ES 数据）。

## 启动顺序

1. `1-start-es.ps1`
2. `5-load-templates.ps1`（**建议在首条日志入库前执行**；使用桌面 **`启动ticket_sys服务.bat`** 时会自动在 ES 就绪后注册模板）
3. `3-start-logstash.ps1`
4. `4-start-filebeat.ps1`
5. `2-start-kibana.ps1`
6. 启动任意微服务产生 `logs/**/*.json.log`

## Filebeat 路径

默认采集路径见 `D:\ELK\Filebeat\Beats\9.3.3\filebeat\filebeat.yml`。若项目不在 OneDrive 该路径下，请修改其中 `paths`。

## 网关与 ticket-common

`ticket-gateway` 未依赖 `ticket-common`，`logback-base.xml` 在网关模块内有一份副本，修改公共配置时请同步 `ticket-common` 与 `ticket-gateway` 两处。
