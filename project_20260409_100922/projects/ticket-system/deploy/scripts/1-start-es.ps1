# 启动 Elasticsearch 9.x（单节点 + TLS）
# 首次或更换 node 名称后若无法启动，可备份并清空 ES 的 data 目录后再试。
Set-Location "D:\ELK\ElasticSearch\elasticsearch-9.3.3\bin"
Write-Host "正在启动 Elasticsearch ..." -ForegroundColor Cyan
& .\elasticsearch.bat
