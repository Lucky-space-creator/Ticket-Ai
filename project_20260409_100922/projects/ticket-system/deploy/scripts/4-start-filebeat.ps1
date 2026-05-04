# 启动 Filebeat（读取项目 logs 下 JSON，发往 Logstash 5044）
Set-Location "D:\ELK\Filebeat\Beats\9.3.3\filebeat"
Write-Host "正在启动 Filebeat ..." -ForegroundColor Cyan
& .\filebeat.exe -c filebeat.yml -e
