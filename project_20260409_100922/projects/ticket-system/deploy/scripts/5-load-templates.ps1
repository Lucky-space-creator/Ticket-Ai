# 向 Elasticsearch 注册索引模板（需 ES 已启动且 elastic 密码可用）
# 启动时：由「启动ticket_sys服务.bat」在 ES 就绪后自动调用；也可单独双击本脚本执行。
$ErrorActionPreference = "Continue"
$deployRoot = Split-Path $PSScriptRoot
$tplDir = Join-Path $deployRoot "elasticsearch\templates"

# 与 bat 中 ELASTIC_PASSWORD / ES_PWD 保持一致；若不同请设置环境变量 ELASTIC_PASSWORD
$elasticPwd = if ($env:ELASTIC_PASSWORD) { $env:ELASTIC_PASSWORD } else { "123456" }
$userArg = "elastic:$elasticPwd"

$appTpl = Join-Path $tplDir "ticket-app-template.json"
$aiTpl = Join-Path $tplDir "ticket-aichat-template.json"

if (-not (Test-Path $appTpl)) { throw "找不到模板: $appTpl" }

Write-Host "PUT _index_template/ticket-app ..." -ForegroundColor Cyan
& curl.exe -k -s -S -u $userArg -X PUT "https://127.0.0.1:9200/_index_template/ticket-app" `
  -H "Content-Type: application/json" `
  --data-binary "@$appTpl"
if ($LASTEXITCODE -ne 0) { Write-Host "ticket-app 模板 PUT 失败，curl 退出码 $LASTEXITCODE" -ForegroundColor Red; exit 1 }
Write-Host ""

Write-Host "PUT _index_template/ticket-aichat ..." -ForegroundColor Cyan
& curl.exe -k -s -S -u $userArg -X PUT "https://127.0.0.1:9200/_index_template/ticket-aichat" `
  -H "Content-Type: application/json" `
  --data-binary "@$aiTpl"
if ($LASTEXITCODE -ne 0) { Write-Host "ticket-aichat 模板 PUT 失败，curl 退出码 $LASTEXITCODE" -ForegroundColor Red; exit 1 }
Write-Host ""
Write-Host "完成。请在 Kibana 中创建 Data View: ticket-app-* 与 ticket-aichat-*" -ForegroundColor Green
exit 0
