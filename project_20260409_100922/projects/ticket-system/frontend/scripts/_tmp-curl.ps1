$loginBody = '{"phone":"13800138000","password":"123456"}'
$login = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json; charset=utf-8"
$tok = $login.data.token
$h = @{ Authorization = "Bearer $tok" }
foreach ($path in @("/api/orders", "/api/orders/fooorder123", "/api/orders/queue/smoke-id", "/api/orders/queue")) {
    $base = "http://127.0.0.1:8083"
    try {
        try {
            $r = Invoke-WebRequest -Uri "$base$path" -Headers $h -UseBasicParsing
            $gv = $r.Headers["X-Gateway-Version"]
            Write-Host "$path ->" $r.StatusCode "gateway=$gv" $r.Content.Substring(0, [Math]::Min(160, $r.Content.Length))
        } catch {
            $resp = $_.Exception.Response
            if ($resp) {
                $gv = $resp.Headers["X-Gateway-Version"]
                $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $body = $sr.ReadToEnd()
                Write-Host "$path ->" $resp.StatusCode "gateway=$gv" $body.Substring(0, [Math]::Min(200, $body.Length))
            } else {
                Write-Host "$path ->" $_
            }
        }
    } catch {
        Write-Host "$path -> outer" $_
    }
}
