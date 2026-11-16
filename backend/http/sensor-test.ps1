# ─────────────────────────────────────────────────────────────────────
# 워치 감지 SOS · 심박 이상 SOS 심박 기록(bpm_log) 자동 테스트 (PowerShell)
#
# 실행 방법 (Redis와 백엔드를 먼저 실행하고, Supabase에 09_erd_20261113.sql을 적용한 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\sensor-test.ps1
#
# 확인하는 것
#   - 워치 감지 SOS는 경로 안내 중에만 (아니면 409), source=MOBILE이면 400, bpm 범위 400
#   - 센서 배치는 진행 중인 "심박 이상 SOS" + 그 SOS를 감지한 워치만 받음 (그 외 409, 깨진 배치 400, 토큰 없음 401)
#   - 심박 이상 SOS 종료 → bpm_log에 발생 당시(감지 값 132)·종료 시(마지막 배치 105) 심박 저장 (DB 확인 SQL 출력)
#   - 낙상 SOS는 센서를 받지 않음 (배치 409, bpm_log 없음)
# 매 실행마다 테스트 계정 1개, 워치 1개, 경로 1건, SOS 2건, bpm_log 1행이 DB에 남는다.
# ─────────────────────────────────────────────────────────────────────


param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(15)

$Run = Get-Random -Minimum 1000 -Maximum 9999
$script:passCount = 0
$script:failCount = 0

# ── HTTP 요청 보내기 (상태 코드와 무관하게 응답을 그대로 돌려준다) ──────────
function Send-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = $null
    )
    $request = New-Object System.Net.Http.HttpRequestMessage(
        (New-Object System.Net.Http.HttpMethod($Method)), "$BaseUrl$Path")

    if ($Token) {
        $request.Headers.Authorization =
            New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10
        $request.Content = New-Object System.Net.Http.StringContent(
            $json, [System.Text.Encoding]::UTF8, "application/json")
    }

    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)

    $parsed = $null
    try { $parsed = $text | ConvertFrom-Json } catch {}

    return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Body   = $parsed
        Raw    = $text
    }
}

# ── 결과 판정 ───────────────────────────────────────────────────────────
# $Extra: 추가 검사. 통과하면 $true, 실패하면 실패 이유(문자열)를 반환하는 scriptblock
function Test-Case {
    param(
        [string]$Name,
        $Result,
        [int]$Expected,
        [scriptblock]$Extra = $null
    )
    $ok = ($Result.Status -eq $Expected)
    $reason = ""

    if ($ok -and $Extra) {
        $check = & $Extra $Result
        if ($check -ne $true) {
            $ok = $false
            $reason = [string]$check
        }
    }

    $message = ""
    if ($Result.Body -and $Result.Body.message) { $message = " - " + $Result.Body.message }

    if ($ok) {
        $script:passCount++
        Write-Host "[PASS] " -ForegroundColor Green -NoNewline
        Write-Host "$Name (HTTP $($Result.Status))$message"
    } else {
        $script:failCount++
        Write-Host "[FAIL] " -ForegroundColor Red -NoNewline
        Write-Host "$Name"
        Write-Host "       기대: HTTP $Expected / 실제: HTTP $($Result.Status)" -ForegroundColor Yellow
        if ($reason) { Write-Host "       이유: $reason" -ForegroundColor Yellow }
        Write-Host "       응답: $($Result.Raw)" -ForegroundColor DarkGray
    }
}

function Get-Token($Result) {
    if ($Result.Body -and $Result.Body.data) { return $Result.Body.data.accessToken }
    return $null
}


# 천안역에서 북쪽으로 3개 구간 (지점 간격 약 111m)
function New-RouteBody {
    $points = @()
    $segments = @()
    for ($i = 0; $i -lt 4; $i++) {
        $p = [ordered]@{ lat = 36.8100 + $i * 0.001; lng = 127.1467 }
        if ($i -eq 0) { $p.name = "천안역" }
        if ($i -eq 3) { $p.name = "목적지" }
        $points += $p
        if ($i -lt 3) { $segments += [ordered]@{ safetyScore = 0.7; riskScore = 0.0; facilities = @() } }
    }
    return [ordered]@{ estimatedTime = 300; safetyScore = 0.7; points = $points; segments = $segments }
}

Add-Type -AssemblyName System.IO.Compression

# 워치 TelemetryStreamer와 같은 배치(gzip: 헤더 JSON 1줄 + CSV). 50Hz 11행(0.2초), 심박 $Bpm
function New-Batch {
    param([string]$DeviceId, [long]$Seq, [long]$StartMs, [int]$Bpm = 80, [double]$Az = 9.8)
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('{"v":1,"deviceId":"' + $DeviceId + '","deviceType":"SM-R910","seq":' + $Seq + ',"startMs":' + $StartMs + ',"fs":50,"cols":"dt,ax,ay,az,gx,gy,gz,bpm"}' + "`n")
    for ($i = 0; $i -le 10; $i++) {
        [void]$sb.Append("$($i * 20),0.1,0.2,$Az,0.01,0.02,0.03,$Bpm`n")
    }
    $raw = [System.Text.Encoding]::UTF8.GetBytes($sb.ToString())
    $ms = New-Object System.IO.MemoryStream
    $gz = New-Object System.IO.Compression.GZipStream($ms, [System.IO.Compression.CompressionMode]::Compress)
    $gz.Write($raw, 0, $raw.Length)
    $gz.Close()
    return ,$ms.ToArray()
}

function Send-Batch {
    param([long]$EmergencyId, [byte[]]$Bytes, [string]$Token)
    $request = New-Object System.Net.Http.HttpRequestMessage(
        [System.Net.Http.HttpMethod]::Post, "$BaseUrl/api/sensors/telemetry?emergencyId=$EmergencyId")
    if ($Token) {
        $request.Headers.Authorization =
            New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    }
    $request.Content = New-Object System.Net.Http.ByteArrayContent(,$Bytes)
    $request.Content.Headers.ContentType =
        New-Object System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream")
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $text = [System.Text.Encoding]::UTF8.GetString($response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
    $parsed = $null
    try { $parsed = $text | ConvertFrom-Json } catch {}
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $parsed; Raw = $text }
}

function Now-Ms { return [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }

# ── 서버 연결 확인 ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "대상 서버: $BaseUrl   (실행 번호: $Run)" -ForegroundColor Cyan
try {
    $null = Send-Api -Method "POST" -Path "/api/auth/login" -Body @{ email = ""; password = "" }
} catch {
    Write-Host "서버에 연결할 수 없습니다. 백엔드(SafewalkApplication)가 실행 중인지 확인하세요." -ForegroundColor Red
    exit 1
}
Write-Host ""

$emails = @("sensoruser$Run@test.com")
$watchId = "watch-S-$Run"

# ── 준비: 사용자, 워치 등록·연결 ───────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[0]; password = "1234"; name = "센서"; phone = "010-9600-$Run"; userType = "ADULT" })
Test-Case "1. 사용자 가입" $r 200
$token = Get-Token $r
$r = Send-Api -Method "PUT" -Path "/api/wearables" -Token $token -Body ([ordered]@{ deviceType = "WEAR_OS"; deviceIdentifier = $watchId; deviceName = "SM-R910" })
$wearableId = $r.Body.data.wearableId
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$wearableId/connection" -Token $token -Body ([ordered]@{ connected = $true })
Test-Case "2. 워치 등록·연결" $r 200

# ── 안내 전 ───────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $token -Body ([ordered]@{ type = "ABNORMAL_HEART_RATE"; source = "WEARABLE"; bpm = 132 })
Test-Case "3. 안내 중이 아닐 때 심박 이상 SOS → 409" $r 409
$r = Send-Batch -EmergencyId 999999999 -Bytes (New-Batch $watchId 0 (Now-Ms)) -Token $token
Test-Case "4. 없는 SOS로 배치 → 409 EMERGENCY_NOT_ACTIVE" $r 409 {
    param($res) if ($res.Body.data.reason -eq "EMERGENCY_NOT_ACTIVE") { $true } else { "reason: $($res.Body.data.reason)" }
}

# ── 안내 시작 + 심박 이상 SOS ─────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/routes" -Token $token -Body (New-RouteBody)
Test-Case "5. 경로 안내 시작" $r 200
$routeId = $r.Body.data.routeId

$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $token -Body ([ordered]@{ type = "ABNORMAL_HEART_RATE"; source = "MOBILE"; bpm = 132 })
Test-Case "6. 심박 이상 SOS를 source=MOBILE로 → 400" $r 400
$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $token -Body ([ordered]@{ type = "ABNORMAL_HEART_RATE"; source = "WEARABLE"; bpm = 300 })
Test-Case "7. bpm 범위 밖(300) → 400" $r 400
$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $token -Body ([ordered]@{ type = "ABNORMAL_HEART_RATE"; source = "WEARABLE"; lat = 36.8105; lng = 127.1467; accuracy = 8; bpm = 132 })
Test-Case "8. 심박 이상 SOS (감지 심박 132) → 201" $r 201
$heartId = $r.Body.data.emergencyId
$r = Send-Api -Method "GET" -Path "/api/emergencies/$heartId" -Token $token
Test-Case "9. SOS 상세: type ABNORMAL_HEART_RATE, source WEARABLE" $r 200 {
    param($res) if ($res.Body.data.type -eq "ABNORMAL_HEART_RATE" -and $res.Body.data.source -eq "WEARABLE") { $true } else { "응답: $($res.Raw)" }
}

# ── SOS 동안 센서 배치 ────────────────────────────────────────────────
$r = Send-Batch -EmergencyId $heartId -Bytes (New-Batch "not-my-watch-$Run" 1 (Now-Ms) 100) -Token $token
Test-Case "10. 다른 워치의 배치 → 409 WEARABLE_NOT_CONNECTED" $r 409 {
    param($res) if ($res.Body.data.reason -eq "WEARABLE_NOT_CONNECTED") { $true } else { "reason: $($res.Body.data.reason)" }
}
$r = Send-Batch -EmergencyId $heartId -Bytes ([byte[]](1, 2, 3)) -Token $token
Test-Case "11. 깨진 배치(gzip 아님) → 400" $r 400
$r = Send-Batch -EmergencyId $heartId -Bytes (New-Batch $watchId 1 (Now-Ms))
Test-Case "12. 토큰 없음 → 401" $r 401

$r = Send-Batch -EmergencyId $heartId -Bytes (New-Batch $watchId 1 (Now-Ms) 120) -Token $token
Test-Case "13. 감지 워치 배치 (심박 120) → 204" $r 204
Start-Sleep -Milliseconds 300
$last = New-Batch $watchId 2 (Now-Ms) 105
$r = Send-Batch -EmergencyId $heartId -Bytes $last -Token $token
Test-Case "14. 감지 워치 배치 (심박 105) → 204" $r 204
$r = Send-Batch -EmergencyId $heartId -Bytes (New-Batch $watchId 0 ((Now-Ms) - 60000) 150) -Token $token
Test-Case "15. 늦게 도착한 오래된 배치(150) → 204 (종료 심박은 105 유지)" $r 204

$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$heartId" -Token $token -Body @{ status = "RESOLVED" }
Test-Case "16. 심박 이상 SOS 종료 → 200 (이때 bpm_log 저장)" $r 200
$r = Send-Batch -EmergencyId $heartId -Bytes $last -Token $token
Test-Case "17. 종료 후 배치 → 409 EMERGENCY_NOT_ACTIVE (앱은 전송 중단)" $r 409 {
    param($res) if ($res.Body.data.reason -eq "EMERGENCY_NOT_ACTIVE") { $true } else { "reason: $($res.Body.data.reason)" }
}

# ── 낙상 SOS는 센서를 받지 않음 ───────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $token -Body ([ordered]@{ type = "FALL"; source = "WEARABLE"; lat = 36.8115; lng = 127.1467 })
Test-Case "18. 낙상 SOS → 201" $r 201
$fallId = $r.Body.data.emergencyId
$r = Send-Batch -EmergencyId $fallId -Bytes (New-Batch $watchId 3 (Now-Ms) 90) -Token $token
Test-Case "19. 낙상 SOS로 배치 → 409 EMERGENCY_NOT_ACTIVE" $r 409
$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$fallId" -Token $token -Body @{ status = "RESOLVED" }
Test-Case "20. 낙상 SOS 종료 → 200" $r 200

$null = Send-Api -Method "PATCH" -Path "/api/routes/$routeId" -Token $token -Body @{ status = "COMPLETED" }

# ── 요약 ──────────────────────────────────────────────────────────────
$total = $script:passCount + $script:failCount
Write-Host ""
if ($script:failCount -eq 0) {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과 - 모두 통과했습니다." -ForegroundColor Green
} else {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과, $($script:failCount)개 실패" -ForegroundColor Red
}
Write-Host ""
Write-Host "DB 확인 (Supabase SQL Editor) — 기대: 심박 이상 SOS $heartId 는 132 → 105 한 행, 낙상 SOS $fallId 는 없음" -ForegroundColor Cyan
Write-Host "  SELECT emergency_id, bpm_start, bpm_end FROM bpm_log WHERE emergency_id IN ($heartId, $fallId);"
Write-Host "  (서버 로그에도 'bpm_log 저장 (emergencyId=$heartId): 발생 132 → 종료 105'가 찍힌다)" -ForegroundColor DarkGray
Write-Host ""
Write-Host "이번 실행에서 만든 테스트 계정: $($emails -join ', ')" -ForegroundColor DarkGray
Write-Host ""

$client.Dispose()
if ($script:failCount -gt 0) { exit 1 }
