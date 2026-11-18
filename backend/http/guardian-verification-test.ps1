# ─────────────────────────────────────────────────────────────────────
# 보호자 확인(인증번호) API 자동 테스트 (PowerShell)
#
# 준비: backend/.env에 아래 한 줄을 넣고 백엔드를 실행한다 (인증번호를 응답 devCode로 받기 위함).
#   GUARDIAN_VERIFICATION_EXPOSE_CODE=true
# DB에는 db/v3/12_guardian_sms_verification_20261117.sql이 적용돼 있어야 한다.
#
# 실행 방법 (backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\guardian-verification-test.ps1
#
# 등장인물: 아이(미성년자)
#   - 엄마: 앱 미가입 → 인증번호로 확인 → 나중에 앱 가입·연결 후 스스로 연결 끊기(동의 철회)
#   - 아빠: 앱 가입 → 연결된 보호자는 인증번호 확인 대상이 아님(409)
#   - 삼촌: 앱 미가입 → 5번 틀려서 잠김
#   - 이모: 앱 미가입 → 확인 후 대표 승계 확인
#   - 남: 아이의 보호자에 접근 → 404
# 뒷부분(25~33)은 미성년자가 믿을 수 있는 보호자(앱 연결 또는 인증번호 확인)를 모두 없앨 수 없는지와 대표 승계 순서를 확인한다.
#
# 매 실행마다 테스트 계정 4개가 DB에 남는다 (마지막에 정리용 정보 출력).
# 인증번호 문자는 서버 로그의 "[문자(콘솔)]" 줄로도 확인할 수 있다.
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


function Find-Guardian($Token, $Name) {
    $all = @((Send-Api -Method "GET" -Path "/api/guardians" -Token $Token).Body.data)
    return ($all | Where-Object { $_.name -eq $Name })
}

function Expect-Reason($Reason, $Remaining = $null) {
    return {
        param($res)
        $d = $res.Body.data
        if ($d.reason -ne $Reason) { return "reason: $($d.reason) (기대 $Reason)" }
        if ($null -ne $Remaining -and $d.remainingAttempts -ne $Remaining) { return "remainingAttempts: $($d.remainingAttempts) (기대 $Remaining)" }
        $true
    }.GetNewClosure()
}

$momPhone = "010-9600-$Run"
$dadPhone = "010-9700-$Run"
$emails = @("gvkid$Run@test.com", "gvdad$Run@test.com", "gvmom$Run@test.com", "gvstranger$Run@test.com")

# ── 준비 ───────────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = $emails[0]; password = "1234"; name = "정아이"; phone = "010-9800-$Run"; userType = "MINOR"
    guardians = @(
        [ordered]@{ name = "엄마"; phone = $momPhone; relationship = "PARENT" },
        [ordered]@{ name = "아빠"; phone = $dadPhone; relationship = "PARENT" },
        [ordered]@{ name = "삼촌"; phone = "010-9900-$Run"; relationship = "FAMILY" }
    )
})
Test-Case "1. 아이 가입 (보호자: 엄마·아빠·삼촌)" $r 200
$kidToken = Get-Token $r
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[1]; password = "1234"; name = "정아빠"; phone = $dadPhone; userType = "ADULT" })
Test-Case "2. 아빠 가입" $r 200
$dadToken = Get-Token $r
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[3]; password = "1234"; name = "남남"; phone = "010-9500-$Run"; userType = "ADULT" })
Test-Case "3. 관계없는 사람 가입" $r 200
$strangerToken = Get-Token $r

$gMom = (Find-Guardian $kidToken "엄마").guardianId
$gDad = (Find-Guardian $kidToken "아빠").guardianId
$gUncle = (Find-Guardian $kidToken "삼촌").guardianId

$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "4. 새 보호자는 모두 미확인 (smsVerified=false)" $r 200 {
    param($res)
    $bad = @($res.Body.data | Where-Object { $_.smsVerified -ne $false })
    if ($bad.Count -gt 0) { return "smsVerified가 false가 아닌 보호자: $($bad.name -join ', ')" }
    $true
}

# ── 인증번호 발송 ────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification" -Token $kidToken
Test-Case "5. 엄마에게 인증번호 발송" $r 200 {
    param($res)
    $d = $res.Body.data
    if (-not $d.expiresAt -or -not $d.resendAvailableAt) { return "expiresAt/resendAvailableAt 없음" }
    if (-not $d.devCode) { return "devCode 없음 → .env에 GUARDIAN_VERIFICATION_EXPOSE_CODE=true를 넣고 서버를 다시 실행하세요" }
    if ($d.devCode -notmatch '^\d{6}$') { return "devCode 형식: $($d.devCode)" }
    $true
}
$momCode = $r.Body.data.devCode
if (-not $momCode) {
    Write-Host ""
    Write-Host "인증번호(devCode)를 받을 수 없어 테스트를 멈춥니다. .env에 GUARDIAN_VERIFICATION_EXPOSE_CODE=true를 넣고 서버를 다시 실행하세요." -ForegroundColor Red
    $client.Dispose()
    exit 1
}
Write-Host "       (서버 로그에 '[문자(콘솔)] 받는 사람: 010-****-$Run' 와 인증번호가 찍혀야 합니다)" -ForegroundColor DarkGray

$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification" -Token $kidToken
Test-Case "6. 1분 안에 다시 발송 → 429" $r 429 {
    param($res) if ($res.Body.data.retryAfterSeconds -ge 1) { $true } else { "retryAfterSeconds: $($res.Body.data.retryAfterSeconds)" }
}

# ── 인증번호 확인 ────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = "12ab" })
Test-Case "7. 6자리 숫자가 아님 → 400" $r 400

$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification/confirm" -Token $kidToken
Test-Case "8. 본문 없음 → 400" $r 400

$wrong = if ($momCode -eq "000000") { "111111" } else { "000000" }
$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = $wrong })
Test-Case "9. 틀린 번호 → 400 MISMATCH, 남은 횟수 4" $r 400 (Expect-Reason "MISMATCH" 4)

$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = $momCode })
Test-Case "10. 맞는 번호 → 200, smsVerified=true" $r 200 {
    param($res)
    if ($res.Body.data.smsVerified -ne $true) { return "smsVerified: $($res.Body.data.smsVerified)" }
    if (-not $res.Body.data.smsVerifiedAt) { return "smsVerifiedAt 없음" }
    $true
}

$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = $momCode })
Test-Case "11. 이미 확인된 보호자에게 다시 확인 → 409" $r 409
$r = Send-Api -Method "POST" -Path "/api/guardians/$gMom/verification" -Token $kidToken
Test-Case "12. 이미 확인된 보호자에게 다시 발송 → 409" $r 409

$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "13. 목록에서 엄마만 확인됨" $r 200 {
    param($res)
    $all = @($res.Body.data)
    $m = $all | Where-Object { $_.guardianId -eq $gMom }
    $u = $all | Where-Object { $_.guardianId -eq $gUncle }
    if ($m.smsVerified -ne $true -or $u.smsVerified -ne $false) { return "엄마 $($m.smsVerified), 삼촌 $($u.smsVerified)" }
    $true
}

# ── 발송하지 않은 보호자 / 잠김 ─────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = "123456" })
Test-Case "14. 발송 전에 확인 → 400 NOT_REQUESTED" $r 400 (Expect-Reason "NOT_REQUESTED")

$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification" -Token $kidToken
$uncleCode = $r.Body.data.devCode
$uncleWrong = if ($uncleCode -eq "000000") { "111111" } else { "000000" }
for ($i = 1; $i -le 4; $i++) {
    $null = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = $uncleWrong })
}
$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = $uncleWrong })
Test-Case "15. 5번째로 틀림 → 400 LOCKED" $r 400 (Expect-Reason "LOCKED" 0)
$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = $uncleCode })
Test-Case "16. 잠긴 뒤에는 맞는 번호도 → 400 LOCKED" $r 400 (Expect-Reason "LOCKED")

# ── 연결된 보호자 / 권한 ─────────────────────────────────────────────────
$null = Send-Api -Method "POST" -Path "/api/guardians/$gDad/invite" -Token $kidToken
$null = Send-Api -Method "POST" -Path "/api/guardians/invitations/$gDad/accept" -Token $dadToken
$r = Send-Api -Method "POST" -Path "/api/guardians/$gDad/verification" -Token $kidToken
Test-Case "17. 앱으로 연결된 아빠에게 발송 → 409" $r 409

$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification" -Token $strangerToken
Test-Case "18. 남의 보호자에게 발송 → 404" $r 404
$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification/confirm" -Token $strangerToken -Body ([ordered]@{ code = "123456" })
Test-Case "19. 남의 보호자 확인 → 404" $r 404
$r = Send-Api -Method "POST" -Path "/api/guardians/$gUncle/verification"
Test-Case "20. 토큰 없이 발송 → 401" $r 401

# ── 동의 철회: 확인된 엄마가 앱에 가입·연결한 뒤 스스로 연결을 끊음 ─────────
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[2]; password = "1234"; name = "정엄마"; phone = $momPhone; userType = "ADULT" })
Test-Case "21. 엄마 앱 가입" $r 200
$momToken = Get-Token $r
$null = Send-Api -Method "POST" -Path "/api/guardians/$gMom/invite" -Token $kidToken
$r = Send-Api -Method "POST" -Path "/api/guardians/invitations/$gMom/accept" -Token $momToken
Test-Case "22. 엄마 초대 수락" $r 200
$r = Send-Api -Method "POST" -Path "/api/guardians/wards/$gMom/leave" -Token $momToken
Test-Case "23. 엄마가 스스로 연결 끊기" $r 200
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "24. 연결을 끊은 엄마는 다시 미확인 (REVOKED, smsVerified=false)" $r 200 {
    param($res)
    $m = @($res.Body.data) | Where-Object { $_.guardianId -eq $gMom }
    if ($m.registrationStatus -ne "REVOKED" -or $m.smsVerified -ne $false) { return "상태 $($m.registrationStatus), smsVerified $($m.smsVerified)" }
    $true
}

# ── 미성년자 보호자 삭제·해제 제한, 대표 승계 ──────────────────────────────
# 지금 상태: 엄마(대표, REVOKED·미확인), 아빠(CONNECTED), 삼촌(미확인) → 믿을 수 있는 보호자는 아빠 1명
$r = Send-Api -Method "POST" -Path "/api/guardians/$gDad/revoke" -Token $kidToken
Test-Case "25. 믿을 수 있는 마지막 보호자(아빠) 연결 해제 → 400" $r 400
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$gDad" -Token $kidToken
Test-Case "26. 믿을 수 있는 마지막 보호자(아빠) 삭제 → 400" $r 400
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$gUncle" -Token $kidToken
Test-Case "27. 확인 안 된 보호자(삼촌) 삭제는 허용" $r 200

$r = Send-Api -Method "POST" -Path "/api/guardians" -Token $kidToken -Body ([ordered]@{
    name = "이모"; phone = "010-9300-$Run"; relationship = "FAMILY"
})
Test-Case "28. 보호자(이모, 앱 미가입) 추가" $r 200
$gAunt = $r.Body.data.guardianId
$r = Send-Api -Method "POST" -Path "/api/guardians/$gAunt/verification" -Token $kidToken
$r = Send-Api -Method "POST" -Path "/api/guardians/$gAunt/verification/confirm" -Token $kidToken -Body ([ordered]@{ code = $r.Body.data.devCode })
Test-Case "29. 이모 인증번호 확인" $r 200

$r = Send-Api -Method "DELETE" -Path "/api/guardians/$gMom" -Token $kidToken
Test-Case "30. 대표(엄마) 삭제" $r 200
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "31. 새 대표는 먼저 등록된 아빠(앱 연결)가 아니라 문자를 받을 수 있는 이모(확인됨)" $r 200 {
    param($res)
    $p = @($res.Body.data | Where-Object { $_.isPrimary -eq $true })
    if ($p.Count -ne 1) { return "대표 수: $($p.Count)" }
    if ($p[0].guardianId -ne $gAunt) { return "대표: $($p[0].name)" }
    $true
}

$r = Send-Api -Method "POST" -Path "/api/guardians/$gDad/revoke" -Token $kidToken
Test-Case "32. 확인된 이모가 있으면 아빠 연결 해제 허용" $r 200
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$gAunt" -Token $kidToken
Test-Case "33. 이제 마지막 믿을 수 있는 보호자(이모) 삭제 → 400" $r 400

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
