# ─────────────────────────────────────────────────────────────────────
# SOS · 보호자 실시간 위치 공유(SSE) 자동 테스트 (PowerShell)
#
# 실행 방법 (백엔드를 먼저 실행해 둔 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\sos-test.ps1
#
# 등장인물: 아이(SOS 당사자, 미성년자)
#   - 삼촌: 대표 보호자, 앱 미가입 → 문자(서버 로그) 대상
#   - 아빠: 앱 연결 + 위치 열람 허용 → 푸시 + 실시간 위치
#   - 엄마: 앱 연결, 위치 열람 안 함 → 푸시만
#   - 남: 아무 관계 없음 → 모든 조회 404
#
# 자동 처리까지 확인하려면 backend/.env에 아래를 넣고 서버를 다시 실행한 뒤 -Timers를 붙인다.
#   EMERGENCY_STALE_SECONDS=3
#   EMERGENCY_LOST_SECONDS=6
#   EMERGENCY_AUTO_CLOSE_SECONDS=15
#   EMERGENCY_WATCHDOG_INTERVAL_MS=1000
#   powershell -ExecutionPolicy Bypass -File .\http\sos-test.ps1 -Timers
# (확인 후 .env에서 지우고 다시 실행할 것. 기본값은 30초 / 180초 / 7200초)
#
# 매 실행마다 테스트 계정 4개가 DB에 남는다 (마지막에 정리용 정보 출력).
# ─────────────────────────────────────────────────────────────────────


param(
    [string]$BaseUrl = "http://localhost:8080",
    # 자동 처리(위치 끊김·자동 종료)까지 확인. .env에 짧은 시간 설정이 있어야 한다 (아래 설명)
    [switch]$Timers
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
        $json = $Body | ConvertTo-Json -Depth 5
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


$script:skipCount = 0
function Skip-Case([string]$Name, [string]$Why) {
    $script:skipCount++
    Write-Host "[SKIP] " -ForegroundColor DarkYellow -NoNewline
    Write-Host "$Name - $Why"
}

# ── SSE(실시간 스트림) 읽기 도우미 ───────────────────────────────────────
$sseClient = New-Object System.Net.Http.HttpClient
$sseClient.Timeout = [System.Threading.Timeout]::InfiniteTimeSpan

function Open-Sse([string]$Path, [string]$Token) {
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, "$BaseUrl$Path")
    $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    $req.Headers.Accept.ParseAdd("text/event-stream")
    $resp = $sseClient.SendAsync($req, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    $stream = $resp.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
    return [pscustomobject]@{
        Status      = [int]$resp.StatusCode
        ContentType = "$($resp.Content.Headers.ContentType)"
        Reader      = $reader
        Pending     = $null
        Closed      = $false
        Response    = $resp
    }
}

# 원하는 이벤트(이름, data에 포함된 글자)가 올 때까지 읽는다. 찾으면 data, 시간 초과·연결 종료면 $null
function Wait-SseEvent($Sse, [string]$EventName, [string]$DataContains = "", [int]$TimeoutSec = 8) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $current = $null
    while ((Get-Date) -lt $deadline) {
        if ($null -eq $Sse.Pending) { $Sse.Pending = $Sse.Reader.ReadLineAsync() }
        $ms = [int][Math]::Max(1, ($deadline - (Get-Date)).TotalMilliseconds)
        try {
            if (-not $Sse.Pending.Wait($ms)) { return $null }
            $line = $Sse.Pending.Result
        } catch {
            $Sse.Closed = $true; return $null
        }
        $Sse.Pending = $null
        if ($null -eq $line) { $Sse.Closed = $true; return $null }
        if ($line.StartsWith("event:")) { $current = $line.Substring(6).Trim() }
        elseif ($line.StartsWith("data:")) {
            $data = $line.Substring(5).Trim()
            if ($current -eq $EventName -and ($DataContains -eq "" -or $data.Contains($DataContains))) { return $data }
        }
        elseif ($line -eq "") { $current = $null }
    }
    return $null
}

# 서버가 연결을 닫을 때까지 읽는다
function Wait-SseClosed($Sse, [int]$TimeoutSec = 5) {
    $null = Wait-SseEvent $Sse "__never__" "" $TimeoutSec
    return $Sse.Closed
}

function Close-Sse($Sse) {
    if ($Sse) { try { $Sse.Response.Dispose() } catch {} }
}

function Find-Noti($Token, [string]$Title, [string]$Contains = "") {
    $res = Send-Api -Method "GET" -Path "/api/notifications?size=50" -Token $Token
    return @($res.Body.data.items | Where-Object { $_.title -eq $Title -and ($Contains -eq "" -or $_.content -like "*$Contains*") })
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

$dadPhone = "010-9100-$Run"
$momPhone = "010-9200-$Run"
$emails = @("sosdad$Run@test.com", "sosmom$Run@test.com", "sosstranger$Run@test.com", "soskid$Run@test.com")

# ── 준비: 계정 4개, 보호자 연결 ─────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[0]; password = "1234"; name = "최아빠"; phone = $dadPhone; userType = "ADULT" })
Test-Case "1. 아빠 가입" $r 200
$dadToken = Get-Token $r
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[1]; password = "1234"; name = "최엄마"; phone = $momPhone; userType = "ADULT" })
Test-Case "2. 엄마 가입" $r 200
$momToken = Get-Token $r
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[2]; password = "1234"; name = "남남"; phone = "010-9300-$Run"; userType = "ADULT" })
Test-Case "3. 관계없는 사람 가입" $r 200
$strangerToken = Get-Token $r

$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = $emails[3]; password = "1234"; name = "최아이"; phone = "010-9400-$Run"; userType = "MINOR"
    guardians = @(
        [ordered]@{ name = "삼촌"; phone = "010-9500-$Run"; relationship = "FAMILY" },
        [ordered]@{ name = "아빠"; phone = $dadPhone; relationship = "PARENT" },
        [ordered]@{ name = "엄마"; phone = $momPhone; relationship = "PARENT" }
    )
})
Test-Case "4. 아이 가입 (보호자: 삼촌=대표·미가입, 아빠, 엄마)" $r 200
$kidToken = Get-Token $r

$gs = @((Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken).Body.data)
$gUncle = ($gs | Where-Object { $_.name -eq "삼촌" }).guardianId
$gDad = ($gs | Where-Object { $_.name -eq "아빠" }).guardianId
$gMom = ($gs | Where-Object { $_.name -eq "엄마" }).guardianId

foreach ($pair in @(@($gDad, $dadToken), @($gMom, $momToken))) {
    $null = Send-Api -Method "POST" -Path "/api/guardians/$($pair[0])/invite" -Token $kidToken
    $null = Send-Api -Method "POST" -Path "/api/guardians/invitations/$($pair[0])/accept" -Token $pair[1]
}
$r = Send-Api -Method "PATCH" -Path "/api/guardians/$gDad" -Token $kidToken -Body ([ordered]@{ canViewLocation = $true })
$r2 = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "5. 아빠·엄마 연결, 아빠만 위치 열람 허용, 삼촌이 대표" $r2 200 {
    param($res)
    $all = @($res.Body.data)
    $d = $all | Where-Object { $_.guardianId -eq $gDad }
    $m = $all | Where-Object { $_.guardianId -eq $gMom }
    $u = $all | Where-Object { $_.guardianId -eq $gUncle }
    if ($d.registrationStatus -ne "CONNECTED" -or $m.registrationStatus -ne "CONNECTED") { return "연결 상태: 아빠 $($d.registrationStatus), 엄마 $($m.registrationStatus)" }
    if ($d.canViewLocation -ne $true -or $m.canViewLocation -ne $false) { return "위치 열람: 아빠 $($d.canViewLocation), 엄마 $($m.canViewLocation)" }
    if ($u.isPrimary -ne $true) { return "삼촌이 대표 보호자가 아님" }
    $true
}

# ── SOS 시작: 입력 검사 ────────────────────────────────────────────────
$bad = @(
    @{ n = "본문 없음"; b = $null },
    @{ n = "type=FALL (아직 미지원)"; b = [ordered]@{ type = "FALL"; source = "WEARABLE" } },
    @{ n = "source 없음"; b = [ordered]@{ type = "SOS" } },
    @{ n = "source=SYSTEM"; b = [ordered]@{ source = "SYSTEM" } },
    @{ n = "lat만 있음"; b = [ordered]@{ source = "MOBILE"; lat = 36.81 } },
    @{ n = "lat 범위 초과"; b = [ordered]@{ source = "MOBILE"; lat = 100; lng = 127.1 } },
    @{ n = "accuracy 음수"; b = [ordered]@{ source = "MOBILE"; lat = 36.81; lng = 127.14; accuracy = -1 } },
    @{ n = "wearableId (아직 미지원)"; b = [ordered]@{ source = "WEARABLE"; wearableId = 1 } }
)
foreach ($c in $bad) {
    $r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $kidToken -Body $c.b
    Test-Case "6. SOS 시작 거부: $($c.n) → 400" $r 400
}
$r = Send-Api -Method "POST" -Path "/api/emergencies" -Body ([ordered]@{ source = "MOBILE" })
Test-Case "7. 토큰 없이 SOS → 401" $r 401

# ── SOS 시작 ───────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $kidToken -Body ([ordered]@{
    type = "SOS"; source = "MOBILE"; lat = 36.8100; lng = 127.1400; accuracy = 12.5
})
Test-Case "8. 아이 SOS 시작 → 201, 푸시 2명·위치 공유 1명·문자 1명" $r 201 {
    param($res)
    $d = $res.Body.data
    if ($d.status -ne "OCCURRED") { return "status: $($d.status)" }
    $n = $d.notified
    if ($n.pushGuardians -ne 2 -or $n.locationSharedGuardians -ne 1 -or $n.smsGuardians -ne 1) {
        return "notified: push $($n.pushGuardians), shared $($n.locationSharedGuardians), sms $($n.smsGuardians)"
    }
    $true
}
$eid = $r.Body.data.emergencyId
Write-Host "       (서버 로그에 '[문자(콘솔)] 받는 사람: 010-****-$Run' 이 찍혀야 합니다)" -ForegroundColor DarkGray

$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $kidToken -Body ([ordered]@{ source = "WEARABLE" })
Test-Case "9. 진행 중인데 또 SOS → 409 + 기존 emergencyId" $r 409 {
    param($res) if ($res.Body.data.emergencyId -eq $eid) { $true } else { "data: $($res.Raw)" }
}

# ── 조회 ───────────────────────────────────────────────────────────────
$r = Send-Api -Method "GET" -Path "/api/emergencies/active" -Token $kidToken
Test-Case "10. 아이 진행 중 SOS 조회" $r 200 {
    param($res) if ($res.Body.data.emergencyId -eq $eid -and $res.Body.data.viewer -eq "SELF") { $true } else { "data: $($res.Raw)" }
}
$r = Send-Api -Method "GET" -Path "/api/emergencies/active" -Token $dadToken
Test-Case "11. 아빠는 자기 SOS 없음 → data null" $r 200 {
    param($res) if ($null -eq $res.Body.data) { $true } else { "data: $($res.Raw)" }
}

$r = Send-Api -Method "GET" -Path "/api/emergencies/shared" -Token $dadToken
Test-Case "12. 아빠: 보호 중인 SOS 목록에 1건, 위치 열람 가능" $r 200 {
    param($res)
    $s = @($res.Body.data)
    if ($s.Count -ne 1 -or $s[0].emergencyId -ne $eid) { return "목록: $($res.Raw)" }
    if ($s[0].canViewLocation -ne $true -or $s[0].userName -ne "최아이") { return "항목: $($res.Raw)" }
    $true
}
$r = Send-Api -Method "GET" -Path "/api/emergencies/shared" -Token $momToken
Test-Case "13. 엄마: 목록 1건, 위치 열람 불가" $r 200 {
    param($res) $s = @($res.Body.data); if ($s.Count -eq 1 -and $s[0].canViewLocation -eq $false) { $true } else { "목록: $($res.Raw)" }
}
$r = Send-Api -Method "GET" -Path "/api/emergencies/shared" -Token $strangerToken
Test-Case "14. 관계없는 사람: 목록 0건" $r 200 {
    param($res) if (@($res.Body.data).Count -eq 0) { $true } else { "목록: $($res.Raw)" }
}

$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid" -Token $dadToken
Test-Case "15. 아빠 상세: GUARDIAN, 발생 위치 보임" $r 200 {
    param($res) $d = $res.Body.data; if ($d.viewer -eq "GUARDIAN" -and $d.initialLocation.lat -eq 36.81) { $true } else { "data: $($res.Raw)" }
}
$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid" -Token $momToken
Test-Case "16. 엄마 상세: 발생 위치 숨김" $r 200 {
    param($res) $d = $res.Body.data; if ($d.canViewLocation -eq $false -and $null -eq $d.initialLocation) { $true } else { "data: $($res.Raw)" }
}
$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid" -Token $strangerToken
Test-Case "17. 관계없는 사람 상세 → 404" $r 404

$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid/locations/latest" -Token $dadToken
Test-Case "18. 아빠 최신 위치 = 발생 위치" $r 200 {
    param($res) if ($res.Body.data.lat -eq 36.81 -and $res.Body.data.accuracy -eq 12.5) { $true } else { "data: $($res.Raw)" }
}
$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid/locations/latest" -Token $momToken
Test-Case "19. 엄마 최신 위치 → 404 (열람 미허용)" $r 404

$dadStart = @(Find-Noti $dadToken "긴급 SOS" "최아이")
$momStart = @(Find-Noti $momToken "긴급 SOS" "최아이")
Test-Case "20. 아빠·엄마 알림함에 '긴급 SOS'" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "" }) 200 {
    param($res)
    if ($dadStart.Count -ne 1 -or $momStart.Count -ne 1) { return "아빠 $($dadStart.Count)건, 엄마 $($momStart.Count)건" }
    if ($dadStart[0].referenceType -ne "EMERGENCY_EVENT" -or $dadStart[0].referenceId -ne $eid) { return "reference: $($dadStart[0].referenceType) $($dadStart[0].referenceId)" }
    if ($dadStart[0].type -ne "EMERGENCY") { return "type: $($dadStart[0].type)" }
    $true
}

# ── 실시간 위치 (SSE) ──────────────────────────────────────────────────
$sse = Open-Sse "/api/emergencies/$eid/stream" $dadToken
Test-Case "21. 아빠 SSE 연결 (text/event-stream)" ([pscustomobject]@{ Status = $sse.Status; Body = $null; Raw = $sse.ContentType }) 200 {
    param($res) if ($sse.ContentType -like "text/event-stream*") { $true } else { "Content-Type: $($sse.ContentType)" }
}
$data = Wait-SseEvent $sse "location" "36.81" 5
Test-Case "22. 연결 직후 현재 위치 1건 수신" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "$data" }) 200 {
    param($res) if ($data) { $true } else { "5초 안에 location 이벤트가 오지 않음" }
}

$now = (Get-Date).ToUniversalTime()
$r = Send-Api -Method "POST" -Path "/api/emergencies/$eid/locations" -Token $kidToken -Body ([ordered]@{
    lat = 36.8155; lng = 127.1466; accuracy = 6.0; recordedAt = $now.ToString("o")
})
Test-Case "23. 아이 위치 전송 → 204" $r 204
$data = Wait-SseEvent $sse "location" "36.8155" 5
Test-Case "24. 아빠 화면에 새 위치 실시간 도착" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "$data" }) 200 {
    param($res) if ($data) { $true } else { "5초 안에 새 위치가 오지 않음" }
}

$r = Send-Api -Method "POST" -Path "/api/emergencies/$eid/locations" -Token $kidToken -Body ([ordered]@{
    lat = 36.7000; lng = 127.0000; recordedAt = $now.AddMinutes(-5).ToString("o")
})
$r2 = Send-Api -Method "GET" -Path "/api/emergencies/$eid/locations/latest" -Token $dadToken
Test-Case "25. 늦게 도착한 옛날 위치는 무시 (최신 위치 유지)" $r2 200 {
    param($res) if ($r.Status -eq 204 -and $res.Body.data.lat -eq 36.8155) { $true } else { "전송 $($r.Status), 최신 $($res.Raw)" }
}

$r = Send-Api -Method "POST" -Path "/api/emergencies/$eid/locations" -Token $kidToken -Body ([ordered]@{ lng = 127.1 })
Test-Case "26. 위치 전송 lat 없음 → 400" $r 400
$r = Send-Api -Method "POST" -Path "/api/emergencies/$eid/locations" -Token $dadToken -Body ([ordered]@{ lat = 36.8; lng = 127.1 })
Test-Case "27. 당사자가 아닌 사람이 위치 전송 → 404" $r 404
$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid/stream" -Token $momToken
Test-Case "28. 엄마 SSE → 404 (열람 미허용)" $r 404
$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid/stream" -Token $strangerToken
Test-Case "29. 관계없는 사람 SSE → 404" $r 404

# ── 종료 ───────────────────────────────────────────────────────────────
$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$eid" -Token $momToken -Body ([ordered]@{ status = "FALSE_ALARM" })
Test-Case "30. 보호자가 FALSE_ALARM으로 종료 → 400" $r 400
$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$eid" -Token $strangerToken -Body ([ordered]@{ status = "RESOLVED" })
Test-Case "31. 관계없는 사람 종료 → 404" $r 404
$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$eid" -Token $kidToken -Body ([ordered]@{ status = "OCCURRED" })
Test-Case "32. 종료 상태가 아닌 값 → 400" $r 400

$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$eid" -Token $dadToken -Body ([ordered]@{ status = "RESOLVED" })
Test-Case "33. 아빠(현장 도착)가 RESOLVED로 종료" $r 200 {
    param($res) $d = $res.Body.data; if ($d.status -eq "RESOLVED" -and $d.resolvedBy.role -eq "GUARDIAN") { $true } else { "data: $($res.Raw)" }
}
$data = Wait-SseEvent $sse "status" "RESOLVED" 5
$closed = Wait-SseClosed $sse 5
Test-Case "34. 아빠 SSE에 종료(status) 이벤트 후 연결 닫힘" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "$data" }) 200 {
    param($res) if ($data -and $closed) { $true } else { "status 이벤트: $data, 닫힘: $closed" }
}
Close-Sse $sse

$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$eid" -Token $kidToken -Body ([ordered]@{ status = "RESOLVED" })
Test-Case "35. 이미 종료된 SOS 종료 → 409" $r 409
$r = Send-Api -Method "POST" -Path "/api/emergencies/$eid/locations" -Token $kidToken -Body ([ordered]@{ lat = 36.8; lng = 127.1 })
Test-Case "36. 종료 후 위치 전송 → 409 (앱은 전송 중지)" $r 409
$r = Send-Api -Method "GET" -Path "/api/emergencies/active" -Token $kidToken
Test-Case "37. 종료 후 진행 중 SOS 없음" $r 200 {
    param($res) if ($null -eq $res.Body.data) { $true } else { "data: $($res.Raw)" }
}
$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid" -Token $kidToken
Test-Case "38. 상세: RESOLVED, 종료자 GUARDIAN" $r 200 {
    param($res) $d = $res.Body.data; if ($d.status -eq "RESOLVED" -and $d.resolvedBy.role -eq "GUARDIAN" -and $d.resolvedAt) { $true } else { "data: $($res.Raw)" }
}

$sse2 = Open-Sse "/api/emergencies/$eid/stream" $dadToken
$data = Wait-SseEvent $sse2 "status" "RESOLVED" 5
$closed = Wait-SseClosed $sse2 5
Test-Case "39. 종료된 SOS에 SSE 연결 → status 1건 후 닫힘" ([pscustomobject]@{ Status = $sse2.Status; Body = $null; Raw = "$data" }) 200 {
    param($res) if ($data -and $closed) { $true } else { "status: $data, 닫힘: $closed" }
}
Close-Sse $sse2

$kidEnd = @(Find-Noti $kidToken "SOS 종료" "아빠님이")
$momEnd = @(Find-Noti $momToken "SOS 종료")
$dadEnd = @(Find-Noti $dadToken "SOS 종료")
Test-Case "40. 종료 알림: 아이·엄마 받음, 직접 종료한 아빠는 안 받음" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "" }) 200 {
    param($res) if ($kidEnd.Count -eq 1 -and $momEnd.Count -eq 1 -and $dadEnd.Count -eq 0) { $true } else { "아이 $($kidEnd.Count), 엄마 $($momEnd.Count), 아빠 $($dadEnd.Count)" }
}

# ── 본인 취소 ──────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $kidToken -Body ([ordered]@{ source = "WEARABLE" })
Test-Case "41. GPS 없이 워치로 새 SOS → 201" $r 201
$eid2 = $r.Body.data.emergencyId
$r = Send-Api -Method "GET" -Path "/api/emergencies/$eid2/locations/latest" -Token $dadToken
Test-Case "42. 위치 없는 SOS의 최신 위치 → data null" $r 200 {
    param($res) if ($null -eq $res.Body.data) { $true } else { "data: $($res.Raw)" }
}
$r = Send-Api -Method "PATCH" -Path "/api/emergencies/$eid2" -Token $kidToken -Body ([ordered]@{ status = "CANCELLED" })
Test-Case "43. 아이가 직접 취소" $r 200 {
    param($res) if ($res.Body.data.resolvedBy.role -eq "SELF") { $true } else { "data: $($res.Raw)" }
}
$dadCancel = @(Find-Noti $dadToken "SOS 종료" "취소")
$kidSelf = @(Find-Noti $kidToken "SOS 종료" "취소")
Test-Case "44. 취소 알림: 아빠는 받고, 직접 취소한 아이는 안 받음" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "" }) 200 {
    param($res) if ($dadCancel.Count -eq 1 -and $kidSelf.Count -eq 0) { $true } else { "아빠 $($dadCancel.Count), 아이 $($kidSelf.Count)" }
}

# ── 자동 처리 (위치 끊김·자동 종료) ──────────────────────────────────────
if (-not $Timers) {
    Skip-Case "45~48. 자동 처리" "-Timers 옵션과 .env 짧은 시간 설정이 필요합니다 (파일 맨 위 설명 참고)"
} else {
    $r = Send-Api -Method "POST" -Path "/api/emergencies" -Token $kidToken -Body ([ordered]@{ source = "MOBILE"; lat = 36.81; lng = 127.14 })
    Test-Case "45. 자동 처리용 SOS 시작" $r 201
    $eid3 = $r.Body.data.emergencyId
    $sse3 = Open-Sse "/api/emergencies/$eid3/stream" $dadToken

    $data = Wait-SseEvent $sse3 "location_stale" "" 10
    Test-Case "46. 위치가 안 오면 SSE location_stale" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "$data" }) 200 {
        param($res) if ($data) { $true } else { "10초 안에 location_stale이 오지 않음 (EMERGENCY_STALE_SECONDS 설정 확인)" }
    }
    Start-Sleep -Seconds 6
    $lost = @(Find-Noti $dadToken "위치 수신 끊김" "최아이" | Where-Object { $_.referenceId -eq $eid3 })
    Test-Case "47. 더 오래 안 오면 아빠에게 '위치 수신 끊김' 알림 1건" ([pscustomobject]@{ Status = 200; Body = $null; Raw = "" }) 200 {
        param($res) if ($lost.Count -eq 1) { $true } else { "알림 $($lost.Count)건 (EMERGENCY_LOST_SECONDS 설정 확인)" }
    }
    $data = Wait-SseEvent $sse3 "status" "CANCELLED" 20
    $r = Send-Api -Method "GET" -Path "/api/emergencies/$eid3" -Token $kidToken
    Test-Case "48. 시간이 지나면 자동 종료 (CANCELLED, 종료자 없음)" $r 200 {
        param($res) $d = $res.Body.data; if ($data -and $d.status -eq "CANCELLED" -and $null -eq $d.resolvedBy) { $true } else { "SSE: $data, 상세: $($res.Raw)" }
    }
    Close-Sse $sse3
}

# ── 요약 ──────────────────────────────────────────────────────────────
$total = $script:passCount + $script:failCount
Write-Host ""
if ($script:failCount -eq 0) {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과 - 모두 통과했습니다. (SKIP $($script:skipCount)개)" -ForegroundColor Green
} else {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과, $($script:failCount)개 실패 (SKIP $($script:skipCount)개)" -ForegroundColor Red
}
Write-Host ""
Write-Host "이번 실행에서 만든 테스트 계정: $($emails -join ', ')" -ForegroundColor DarkGray
Write-Host ""

$client.Dispose()
$sseClient.Dispose()
if ($script:failCount -gt 0) { exit 1 }
