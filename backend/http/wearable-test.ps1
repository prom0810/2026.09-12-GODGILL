# ─────────────────────────────────────────────────────────────────────
# 웨어러블 기기 등록 · 연결 자동 테스트 (PowerShell)
#
# 실행 방법 (백엔드를 먼저 실행해 둔 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\wearable-test.ps1
#
# 등장인물: 사용자 + 다른 사람
# 확인하는 것
#   - 기기 등록·갱신(등록만으로는 연결 안 됨)·목록·삭제
#   - 연결 규칙: 내 다른 워치 연결 중 → 409 SWITCH_REQUIRED → 확인 후 전환, 안내 중 전환 불가,
#     같은 워치를 다른 계정에 등록은 허용·연결은 거부(IN_USE_BY_OTHER_ACCOUNT), 해제, 삭제 시 해제
#   - 남의 기기 404, 삭제된 기기 404, 입력 검사 400, 토큰 없음 401
# (다른 계정 연결 만료(기본 24시간) 해제는 시간이 걸려 여기서는 확인하지 않는다)
# 매 실행마다 테스트 계정 2개와 경로 1건이 DB에 남는다.
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

$emails = @("wearuser$Run@test.com", "wearother$Run@test.com")

# ── 준비: 사용자, 다른 사람 ─────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[0]; password = "1234"; name = "정워치"; phone = "010-9700-$Run"; userType = "ADULT" })
Test-Case "1. 사용자 가입" $r 200
$userToken = Get-Token $r
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[1]; password = "1234"; name = "남남"; phone = "010-9800-$Run"; userType = "ADULT" })
Test-Case "2. 다른 사람 가입" $r 200
$otherToken = Get-Token $r

# ── 등록 ─────────────────────────────────────────────────────────────
$r = Send-Api -Method "PUT" -Path "/api/wearables" -Token $userToken -Body ([ordered]@{ deviceType = "WEAR_OS"; deviceIdentifier = "watch-A-$Run"; deviceName = "SM-R910" })
Test-Case "3. 워치 A 등록 → 등록만으로는 연결 안 됨" $r 200 {
    param($res) if (-not $res.Body.data.isActive -and $res.Body.data.deviceType -eq "WEAR_OS") { $true } else { "응답: $($res.Raw)" }
}
$watchA = $r.Body.data.wearableId

$r = Send-Api -Method "PUT" -Path "/api/wearables" -Token $userToken -Body ([ordered]@{ deviceType = "WEAR_OS"; deviceIdentifier = "watch-A-$Run"; deviceName = "Galaxy Watch7" })
Test-Case "4. 같은 워치 재등록 → 같은 ID, 이름 갱신" $r 200 {
    param($res)
    if ($res.Body.data.wearableId -ne $watchA) { return "새 ID가 생김: $($res.Body.data.wearableId)" }
    if ($res.Body.data.deviceName -ne "Galaxy Watch7") { return "이름: $($res.Body.data.deviceName)" }
    $true
}

# ── 연결·전환 ─────────────────────────────────────────────────────────
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchA/connection" -Token $userToken -Body ([ordered]@{ connected = $true })
Test-Case "5. 워치 A 연결" $r 200 {
    param($res) if ($res.Body.data.wearable.isActive -and $null -eq $res.Body.data.disconnectedWearableId) { $true } else { "응답: $($res.Raw)" }
}

$r = Send-Api -Method "PUT" -Path "/api/wearables" -Token $userToken -Body ([ordered]@{ deviceType = "WEAR_OS"; deviceIdentifier = "watch-B-$Run" })
$watchB = $r.Body.data.wearableId
$r = Send-Api -Method "GET" -Path "/api/wearables" -Token $userToken
Test-Case "6. 워치 B 등록 → 연결 중인 기기는 그대로 A" $r 200 {
    param($res)
    $list = @($res.Body.data)
    if ($list.Count -ne 2) { return "기기 수: $($list.Count)" }
    $active = @($list | Where-Object { $_.isActive })
    if ($active.Count -ne 1 -or $active[0].wearableId -ne $watchA) { return "연결 중: $($active | ConvertTo-Json -Compress)" }
    $true
}

$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $userToken -Body ([ordered]@{ connected = $true })
Test-Case "7. A 연결 중에 B 연결 → 409 SWITCH_REQUIRED + 기존 기기(A)" $r 409 {
    param($res)
    if ($res.Body.data.reason -ne "SWITCH_REQUIRED") { return "reason: $($res.Body.data.reason)" }
    if ($res.Body.data.currentWearable.wearableId -ne $watchA) { return "currentWearable: $($res.Raw)" }
    $true
}

$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $userToken -Body ([ordered]@{ connected = $true; replaceCurrent = $true })
Test-Case "8. 사용자 확인 후 전환(replaceCurrent) → B 연결, A 해제" $r 200 {
    param($res)
    if (-not $res.Body.data.wearable.isActive) { return "B 연결 안 됨: $($res.Raw)" }
    if ($res.Body.data.disconnectedWearableId -ne $watchA) { return "disconnectedWearableId: $($res.Body.data.disconnectedWearableId)" }
    $true
}

# ── 다른 계정 ─────────────────────────────────────────────────────────
$r = Send-Api -Method "PUT" -Path "/api/wearables" -Token $otherToken -Body ([ordered]@{ deviceType = "WEAR_OS"; deviceIdentifier = "watch-B-$Run" })
Test-Case "9. 다른 사람이 같은 워치 B 등록 → 허용 (연결은 안 됨)" $r 200 {
    param($res) if ($res.Body.data.wearableId -ne $watchB -and -not $res.Body.data.isActive) { $true } else { "응답: $($res.Raw)" }
}
$otherWatchB = $r.Body.data.wearableId

$r = Send-Api -Method "PATCH" -Path "/api/wearables/$otherWatchB/connection" -Token $otherToken -Body ([ordered]@{ connected = $true; replaceCurrent = $true })
Test-Case "10. 사용자가 연결 중인 워치를 다른 사람이 연결 → 409 IN_USE_BY_OTHER_ACCOUNT (상대 정보 없음)" $r 409 {
    param($res)
    if ($res.Body.data.reason -ne "IN_USE_BY_OTHER_ACCOUNT") { return "reason: $($res.Body.data.reason)" }
    if ($null -ne $res.Body.data.currentWearable) { return "상대 기기 정보가 노출됨: $($res.Raw)" }
    $true
}

$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $otherToken -Body ([ordered]@{ connected = $false })
Test-Case "11. 남의 기기 연결 해제 → 404" $r 404

# ── 입력 검사 ─────────────────────────────────────────────────────────
$r = Send-Api -Method "PUT" -Path "/api/wearables" -Token $userToken -Body ([ordered]@{ deviceType = "SMART_RING"; deviceIdentifier = "x" })
Test-Case "12. 알 수 없는 deviceType → 400" $r 400
$r = Send-Api -Method "PUT" -Path "/api/wearables" -Token $userToken -Body ([ordered]@{ deviceType = "WEAR_OS" })
Test-Case "13. deviceIdentifier 없음 → 400" $r 400
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $userToken -Body ([ordered]@{ replaceCurrent = $true })
Test-Case "14. connected 없음 → 400" $r 400

# ── 경로 안내 중 ──────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body (New-RouteBody)
Test-Case "15. 경로 안내 시작" $r 200
$routeId = $r.Body.data.routeId

$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchA/connection" -Token $userToken -Body ([ordered]@{ connected = $true; replaceCurrent = $true })
Test-Case "16. 안내 중 다른 워치로 전환 → 409 GUIDANCE_IN_PROGRESS (replaceCurrent여도)" $r 409 {
    param($res) if ($res.Body.data.reason -eq "GUIDANCE_IN_PROGRESS" -and $res.Body.data.currentWearable.wearableId -eq $watchB) { $true } else { "응답: $($res.Raw)" }
}

$before = @((Send-Api -Method "GET" -Path "/api/wearables" -Token $userToken).Body.data | Where-Object { $_.wearableId -eq $watchB })[0].lastConnectedAt
Start-Sleep -Milliseconds 50
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $userToken -Body ([ordered]@{ connected = $true })
Test-Case "17. 이미 연결 중인 워치 다시 연결 → 시각만 갱신 (앱 재시작·안내 시작 확인)" $r 200 {
    param($res)
    if (-not $res.Body.data.wearable.isActive) { return "연결 해제됨: $($res.Raw)" }
    if ([datetimeoffset]$res.Body.data.wearable.lastConnectedAt -le [datetimeoffset]$before) { return "시각이 갱신되지 않음: $before → $($res.Body.data.wearable.lastConnectedAt)" }
    $true
}

$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $userToken -Body ([ordered]@{ connected = $false })
Test-Case "18. 안내 중에도 연결 해제 가능 (블루투스 끊김·앱에서 해제)" $r 200 {
    param($res) if (-not $res.Body.data.wearable.isActive) { $true } else { "응답: $($res.Raw)" }
}

# ── 해제 후 다른 계정 ─────────────────────────────────────────────────
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$otherWatchB/connection" -Token $otherToken -Body ([ordered]@{ connected = $true })
Test-Case "19. 사용자가 해제한 뒤에는 다른 사람이 같은 워치 연결 가능" $r 200 {
    param($res) if ($res.Body.data.wearable.isActive) { $true } else { "응답: $($res.Raw)" }
}
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $userToken -Body ([ordered]@{ connected = $true })
Test-Case "20. 이번엔 사용자가 다시 연결 → 409 IN_USE_BY_OTHER_ACCOUNT" $r 409 {
    param($res) if ($res.Body.data.reason -eq "IN_USE_BY_OTHER_ACCOUNT") { $true } else { "응답: $($res.Raw)" }
}
$r = Send-Api -Method "DELETE" -Path "/api/wearables/$otherWatchB" -Token $otherToken
Test-Case "21. 다른 사람이 워치 삭제 → 연결도 해제" $r 200
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchB/connection" -Token $userToken -Body ([ordered]@{ connected = $true })
Test-Case "22. 사용자가 다시 연결" $r 200 {
    param($res) if ($res.Body.data.wearable.isActive) { $true } else { "응답: $($res.Raw)" }
}

# ── 삭제, 인증 ───────────────────────────────────────────────────────
$r = Send-Api -Method "DELETE" -Path "/api/wearables/$watchA" -Token $userToken
Test-Case "23. 워치 A 삭제" $r 200
$r = Send-Api -Method "PATCH" -Path "/api/wearables/$watchA/connection" -Token $userToken -Body ([ordered]@{ connected = $true })
Test-Case "24. 삭제된 워치 연결 → 404" $r 404
$r = Send-Api -Method "GET" -Path "/api/wearables" -Token $userToken
Test-Case "25. 목록에서 삭제된 워치 제외" $r 200 {
    param($res) $list = @($res.Body.data); if ($list.Count -eq 1 -and $list[0].wearableId -eq $watchB) { $true } else { "응답: $($res.Raw)" }
}
$r = Send-Api -Method "GET" -Path "/api/wearables"
Test-Case "26. 토큰 없음 → 401" $r 401

$null = Send-Api -Method "PATCH" -Path "/api/routes/$routeId" -Token $userToken -Body @{ status = "COMPLETED" }

# ── 요약 ──────────────────────────────────────────────────────────────
$total = $script:passCount + $script:failCount
Write-Host ""
if ($script:failCount -eq 0) {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과 - 모두 통과했습니다." -ForegroundColor Green
} else {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과, $($script:failCount)개 실패" -ForegroundColor Red
}
Write-Host ""
Write-Host "이번 실행에서 만든 테스트 계정: $($emails -join ', ')" -ForegroundColor DarkGray
Write-Host ""

$client.Dispose()
if ($script:failCount -gt 0) { exit 1 }
