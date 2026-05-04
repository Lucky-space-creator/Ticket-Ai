# 启动 Logstash（Beats 5044 -> ES）
# 方式一：环境变量 ES_PWD（与 elasticsearch-reset-password 为 elastic 设置的密码一致）
# 方式二：在 Logstash 目录执行 bin\logstash-keystore create 后 bin\logstash-keystore add ES_PWD
$env:ES_PWD = if ($env:ES_PWD) { $env:ES_PWD } else { "123456" }
Set-Location "D:\ELK\Logstash\logstash-9.3.3\bin"
Write-Host "Logstash 使用 ES_PWD 环境变量连接 Elasticsearch" -ForegroundColor Yellow
& .\logstash.bat -f "..\config\ticket-pipeline.conf"
