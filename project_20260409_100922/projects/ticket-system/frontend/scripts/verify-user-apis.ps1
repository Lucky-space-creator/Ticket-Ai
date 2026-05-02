param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$Phone = "13800138000",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0

function Step-Api {
    param(
        [string]$Label,
        [scriptblock]$Action
    )
    try {
        & $Action
        Write-Host "[OK]   $Label" -ForegroundColor Green
        $script:pass++
    } catch {
        Write-Host "[FAIL] $Label  $($_.Exception.Message)" -ForegroundColor Red
        $script:fail++
    }
}

Write-Host "=== User frontend API smoke: $BaseUrl (restart gateway after jwt secret change) ===" -ForegroundColor Cyan

$loginBody = (@{ phone = $Phone; password = $Password } | ConvertTo-Json -Compress)
$login = $null
try {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json; charset=utf-8"
} catch {
    Write-Host "[FAIL] POST /api/auth/login  $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
if ($login.code -ne 200 -or -not $login.data.token) {
    Write-Host "[FAIL] POST /api/auth/login no token in body" -ForegroundColor Red
    exit 1
}
Write-Host "[OK]   POST /api/auth/login" -ForegroundColor Green
$script:pass++

$tok = $login.data.token
$auth = @{ Authorization = "Bearer $tok" }

Step-Api "GET /api/trains/search" {
    $u = "$BaseUrl/api/trains/search?startStation=%E5%8C%97%E4%BA%AC&endStation=%E4%B8%8A%E6%B5%B7&trainDate=2026-05-10"
    $r = Invoke-RestMethod -Uri $u -Method Get
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "GET /api/user/profile" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/user/profile" -Method Get -Headers $auth
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "GET /api/passengers" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/passengers" -Method Get -Headers $auth
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "GET /api/orders" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/orders" -Method Get -Headers $auth
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "GET /api/orders/queue/smoke-id" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/orders/queue/smoke-id" -Method Get -Headers $auth
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "GET /api/customer-service/session-id" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/customer-service/session-id" -Method Get -Headers $auth
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "GET /api/customer-service/user-history" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/customer-service/user-history" -Method Get -Headers $auth
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "POST /api/customer-service/request-human" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/customer-service/request-human" -Method Post -Headers $auth -Body "{}" -ContentType "application/json; charset=utf-8"
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "POST /api/customer-service/user-send-message" {
    $b = (@{ content = "smoke" } | ConvertTo-Json -Compress)
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/customer-service/user-send-message" -Method Post -Headers $auth -Body $b -ContentType "application/json; charset=utf-8"
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Step-Api "POST /api/chat/clear" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/chat/clear" -Method Post -Headers $auth -Body "{}" -ContentType "application/json; charset=utf-8"
    if ($r.code -ne 200) { throw "code=$($r.code)" }
}

Write-Host "=== Done: pass $script:pass, fail $script:fail ===" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
