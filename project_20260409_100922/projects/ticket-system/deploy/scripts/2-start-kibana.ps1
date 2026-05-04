# 启动 Kibana（连接 https://127.0.0.1:9200）
# 请确保 ES 用户 elastic 的密码与 kibana.yml 一致（学习版默认 123456）
Set-Location "D:\ELK\Kibana\kibana-9.3.3\bin"
Write-Host "正在启动 Kibana ..." -ForegroundColor Cyan
& .\kibana.bat
