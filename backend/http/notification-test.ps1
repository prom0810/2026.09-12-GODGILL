# ─────────────────────────────────────────────────────────────────────
# 알림함 API + 보호자 연결 알림 자동 테스트 (PowerShell)
#
# 실행 방법 (백엔드를 먼저 실행해 둔 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\notification-test.ps1
#
# 시나리오: 아빠 계정과, 아빠를 보호자로 등록한 미성년자(아이) 계정을 만들고
#   초대 → 거절 → 재초대 → 취소 → 초대·수락 → 연결 끊기 → 해제 → 삭제 순으로 진행하며
#   상대방 알림함에 알림이 정확히 쌓이는지(그리고 거절·취소는 알리지 않는지) 확인한다.
# 기기 토큰을 등록하지 않으므로 푸시는 0대로 발송되고, 알림함 기록만 확인한다.
#
# 매 실행마다 테스트 계정 2개가 DB에 남는다 (마지막에 정리용 정보 출력).
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


function Get-Notis($Token, [string]$Query = "") {
    return Send-Api -Method "GET" -Path "/api/notifications$Query" -Token $Token
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

$dadEmail = "notidad$Run@test.com"
$kidEmail = "notikid$Run@test.com"
$dadPhone = "010-8100-$Run"

# ── 준비 ───────────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = $dadEmail; password = "1234"; name = "박아빠"; phone = $dadPhone; userType = "ADULT"
})
Test-Case "1. 아빠 계정 가입" $r 200
$dadToken = Get-Token $r

$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = $kidEmail; password = "1234"; name = "박아이"; phone = "010-8200-$Run"; userType = "MINOR"
    guardians = @([ordered]@{ name = "우리아빠"; phone = $dadPhone; relationship = "PARENT" })
})
Test-Case "2. 아이 계정 가입 (아빠를 보호자로 등록)" $r 200
$kidToken = Get-Token $r

$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
$gDad = @($r.Body.data)[0].guardianId
Test-Case "3. 아이의 보호자 목록에서 아빠 ID 확인" $r 200 {
    param($res) if ($gDad) { $true } else { "guardianId 없음" }
}

$r = Send-Api -Method "GET" -Path "/api/notifications/unread-count" -Token $dadToken
Test-Case "4. 처음엔 아빠 안 읽은 알림 0" $r 200 {
    param($res) if ($res.Body.data.unreadCount -eq 0) { $true } else { "unreadCount: $($res.Body.data.unreadCount)" }
}

# ── 초대 → 거절 → 재초대 → 취소 ────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/guardians/$gDad/invite" -Token $kidToken
Test-Case "5. 아이가 아빠에게 연결 초대" $r 200

$r = Get-Notis $dadToken
Test-Case "6. 아빠 알림함에 '보호자 연결 요청' 1건" $r 200 {
    param($res)
    $d = $res.Body.data; $n = @($d.items)[0]
    if (@($d.items).Count -ne 1) { return "알림 수: $(@($d.items).Count)" }
    if ($n.type -ne "GUARDIAN") { return "type: $($n.type)" }
    if ($n.title -ne "보호자 연결 요청") { return "title: $($n.title)" }
    if ($n.referenceType -ne "GUARDIAN" -or $n.referenceId -ne $gDad) { return "reference: $($n.referenceType) $($n.referenceId)" }
    if ($n.read -ne $false) { return "읽음 상태가 아님이어야 함" }
    if ($n.content -notlike "*박아이*") { return "content: $($n.content)" }
    if ($d.unreadCount -ne 1) { return "unreadCount: $($d.unreadCount)" }
    $true
}

$r = Get-Notis $kidToken
Test-Case "7. 초대한 아이에게는 알림 없음" $r 200 {
    param($res) if (@($res.Body.data.items).Count -eq 0) { $true } else { "알림 수: $(@($res.Body.data.items).Count)" }
}

$r = Send-Api -Method "POST" -Path "/api/guardians/invitations/$gDad/reject" -Token $dadToken
Test-Case "8. 아빠가 초대 거절" $r 200

$r = Get-Notis $kidToken
Test-Case "9. 거절은 아이에게 알리지 않음" $r 200 {
    param($res) if (@($res.Body.data.items).Count -eq 0) { $true } else { "알림 수: $(@($res.Body.data.items).Count)" }
}

$null = Send-Api -Method "POST" -Path "/api/guardians/$gDad/invite" -Token $kidToken
$r = Send-Api -Method "POST" -Path "/api/guardians/$gDad/revoke" -Token $kidToken
Test-Case "10. 재초대 후 아이가 초대 취소" $r 200

$r = Send-Api -Method "GET" -Path "/api/notifications/unread-count" -Token $dadToken
Test-Case "11. 아빠 안 읽은 알림 2 (초대 2번, 취소는 알리지 않음)" $r 200 {
    param($res) if ($res.Body.data.unreadCount -eq 2) { $true } else { "unreadCount: $($res.Body.data.unreadCount)" }
}

# ── 수락 → 보호자가 연결 끊기 ───────────────────────────────────────────
$null = Send-Api -Method "POST" -Path "/api/guardians/$gDad/invite" -Token $kidToken
$r = Send-Api -Method "POST" -Path "/api/guardians/invitations/$gDad/accept" -Token $dadToken
Test-Case "12. 초대 후 아빠가 수락" $r 200

$r = Get-Notis $kidToken
Test-Case "13. 아이 알림함에 '보호자 연결 완료'" $r 200 {
    param($res)
    $n = @($res.Body.data.items)[0]
    if (@($res.Body.data.items).Count -ne 1) { return "알림 수: $(@($res.Body.data.items).Count)" }
    if ($n.title -ne "보호자 연결 완료") { return "title: $($n.title)" }
    if ($n.content -notlike "*우리아빠*") { return "content: $($n.content)" }
    $true
}

$r = Send-Api -Method "POST" -Path "/api/guardians/wards/$gDad/leave" -Token $dadToken
Test-Case "14. 아빠가 스스로 연결 끊기" $r 200

$r = Get-Notis $kidToken
Test-Case "15. 아이 알림함에 '보호자 연결 해제' (최신순 맨 위)" $r 200 {
    param($res)
    $items = @($res.Body.data.items)
    if ($items.Count -ne 2) { return "알림 수: $($items.Count)" }
    if ($items[0].title -ne "보호자 연결 해제") { return "맨 위 title: $($items[0].title)" }
    if ($items[0].content -notlike "*우리아빠*") { return "content: $($items[0].content)" }
    $true
}

# ── 사용자가 연결 해제 / 보호자 삭제 ────────────────────────────────────
$null = Send-Api -Method "POST" -Path "/api/guardians/$gDad/invite" -Token $kidToken
$null = Send-Api -Method "POST" -Path "/api/guardians/invitations/$gDad/accept" -Token $dadToken
$r = Send-Api -Method "POST" -Path "/api/guardians/$gDad/revoke" -Token $kidToken
Test-Case "16. 다시 연결 후 아이가 연결 해제" $r 200

$r = Get-Notis $dadToken "?size=1"
Test-Case "17. 아빠 알림함 맨 위 '보호자 연결 해제'" $r 200 {
    param($res)
    $n = @($res.Body.data.items)[0]
    if ($n.title -ne "보호자 연결 해제") { return "title: $($n.title)" }
    if ($n.content -notlike "*박아이*") { return "content: $($n.content)" }
    $true
}

$null = Send-Api -Method "POST" -Path "/api/guardians/$gDad/invite" -Token $kidToken
$null = Send-Api -Method "POST" -Path "/api/guardians/invitations/$gDad/accept" -Token $dadToken
$r = Send-Api -Method "POST" -Path "/api/guardians" -Token $kidToken -Body ([ordered]@{
    name = "박삼촌"; phone = "010-8300-$Run"; relationship = "FAMILY"
})
Test-Case "18. 아이가 보호자(삼촌, 미가입) 추가" $r 200
$gUncle = $r.Body.data.guardianId

$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/invite" -Token $kidToken
Test-Case "19. 가입하지 않은 보호자 초대 → 200 (알림 받을 계정 없음)" $r 200

$r = Send-Api -Method "DELETE" -Path "/api/guardians/$gDad" -Token $kidToken
Test-Case "20. 연결된 아빠를 보호자 목록에서 삭제" $r 200

$r = Send-Api -Method "GET" -Path "/api/notifications/unread-count" -Token $dadToken
Test-Case "21. 아빠 안 읽은 알림 7 (초대 5 + 연결 해제 1 + 삭제 1)" $r 200 {
    param($res) if ($res.Body.data.unreadCount -eq 7) { $true } else { "unreadCount: $($res.Body.data.unreadCount)" }
}

# ── 목록 나눠 보기 ─────────────────────────────────────────────────────
$r = Get-Notis $dadToken "?page=0&size=3"
Test-Case "22. size=3 첫 페이지: 3건, 다음 있음" $r 200 {
    param($res)
    $d = $res.Body.data
    if (@($d.items).Count -ne 3) { return "알림 수: $(@($d.items).Count)" }
    if ($d.hasNext -ne $true) { return "hasNext: $($d.hasNext)" }
    if ($d.unreadCount -ne 7) { return "unreadCount: $($d.unreadCount)" }
    $true
}
$latestId = @($r.Body.data.items)[0].notificationId

$r = Get-Notis $dadToken "?page=2&size=3"
Test-Case "23. size=3 세 번째 페이지: 1건, 다음 없음" $r 200 {
    param($res)
    $d = $res.Body.data
    if (@($d.items).Count -ne 1) { return "알림 수: $(@($d.items).Count)" }
    if ($d.hasNext -ne $false) { return "hasNext: $($d.hasNext)" }
    $true
}

foreach ($q in @("?size=0", "?size=51", "?page=-1", "?size=abc")) {
    $r = Get-Notis $dadToken $q
    Test-Case "24. 잘못된 목록 요청 $q → 400" $r 400
}

# ── 읽음 처리 ──────────────────────────────────────────────────────────
$r = Send-Api -Method "PATCH" -Path "/api/notifications/$latestId/read" -Token $dadToken
Test-Case "25. 알림 1건 읽음" $r 200 {
    param($res) if ($res.Body.data.read -eq $true -and $res.Body.data.readAt) { $true } else { "read: $($res.Body.data.read)" }
}
$firstReadAt = $r.Body.data.readAt

$r = Send-Api -Method "PATCH" -Path "/api/notifications/$latestId/read" -Token $dadToken
Test-Case "26. 같은 알림 다시 읽음 → 200, 처음 읽은 시각 유지" $r 200 {
    param($res)
    # 같은 시각인지 비교 (표기 형식·시간대 차이는 무시)
    $a = [DateTimeOffset]$firstReadAt
    $b = [DateTimeOffset]$res.Body.data.readAt
    if ([Math]::Abs(($b - $a).TotalMilliseconds) -lt 1) { $true } else { "readAt 바뀜: $firstReadAt → $($res.Body.data.readAt)" }
}

$r = Send-Api -Method "PATCH" -Path "/api/notifications/$latestId/read" -Token $kidToken
Test-Case "27. 다른 사람 알림 읽음 → 404" $r 404

$r = Send-Api -Method "PATCH" -Path "/api/notifications/999999999/read" -Token $dadToken
Test-Case "28. 없는 알림 읽음 → 404" $r 404

$r = Send-Api -Method "PATCH" -Path "/api/notifications/abc/read" -Token $dadToken
Test-Case "29. 알림 ID 형식 오류 → 400" $r 400

$r = Send-Api -Method "PATCH" -Path "/api/notifications/read-all" -Token $dadToken
Test-Case "30. 전체 읽음 → 남은 6건 처리" $r 200 {
    param($res) if ($res.Body.data.updatedCount -eq 6) { $true } else { "updatedCount: $($res.Body.data.updatedCount)" }
}

$r = Send-Api -Method "GET" -Path "/api/notifications/unread-count" -Token $dadToken
Test-Case "31. 전체 읽음 후 안 읽은 알림 0" $r 200 {
    param($res) if ($res.Body.data.unreadCount -eq 0) { $true } else { "unreadCount: $($res.Body.data.unreadCount)" }
}

$r = Send-Api -Method "GET" -Path "/api/notifications"
Test-Case "32. 토큰 없이 알림함 → 401" $r 401

# ── 요약 ──────────────────────────────────────────────────────────────
$total = $script:passCount + $script:failCount
Write-Host ""
if ($script:failCount -eq 0) {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과 - 모두 통과했습니다." -ForegroundColor Green
} else {
    Write-Host "결과: $($total)개 중 $($script:passCount)개 통과, $($script:failCount)개 실패" -ForegroundColor Red
}
Write-Host ""
Write-Host "이번 실행에서 만든 테스트 계정: $dadEmail, $kidEmail" -ForegroundColor DarkGray
Write-Host ""

$client.Dispose()
if ($script:failCount -gt 0) { exit 1 }
