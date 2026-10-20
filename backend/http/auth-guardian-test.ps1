# ─────────────────────────────────────────────────────────────────────
# 회원가입 · JWT 인증 · 보호자 API(추가·조회·수정·삭제) 자동 테스트 (PowerShell)
#
# auth-guardian.http 와 같은 시나리오를 IntelliJ 라이선스 없이 실행한다.
#
# 실행 방법 (백엔드를 먼저 실행해 둔 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\auth-guardian-test.ps1
#
# 매 실행마다 이메일·전화번호 끝자리를 무작위로 바꾸므로 여러 번 실행해도 된다.
# 단, 실행할 때마다 테스트 계정이 DB에 남는다 (마지막에 정리용 정보 출력).
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

$adultEmail = "adult$Run@test.com"
$kidEmail   = "kid$Run@test.com"

# 1. 성인 가입 (보호자 없음) → 200
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email    = $adultEmail
    password = "1234"
    name     = "이성인"
    phone    = "010-1111-$Run"
    userType = "ADULT"
})
Test-Case "1. 성인 가입 (보호자 없음)" $r 200
$adultToken = Get-Token $r

# 2. 미성년자 가입 (보호자 2명, 전화번호 형식 제각각, 관계는 코드/한글 혼용) → 200
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email     = $kidEmail
    password  = "1234"
    name      = "김안심"
    phone     = "0102222$Run"
    userType  = "MINOR"
    guardians = @(
        [ordered]@{ name = "김엄마"; phone = "010 3333 $Run"; relationship = "PARENT" },
        [ordered]@{ name = "김삼촌"; phone = "010.4444.$Run"; relationship = "가족" }
    )
})
Test-Case "2. 미성년자 가입 (보호자 2명 포함)" $r 200
$kidToken = Get-Token $r

# 3. 미성년자 가입 (보호자 없음) → 400
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email    = "kid-noguardian$Run@test.com"
    password = "1234"
    name     = "박미성"
    phone    = "010-5555-$Run"
    userType = "MINOR"
})
Test-Case "3. 미성년자 가입 (보호자 없음) 거부" $r 400

# 4. 전화번호 중복 가입 (1번과 같은 번호, 형식만 다름) → 409
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email    = "dup$Run@test.com"
    password = "1234"
    name     = "중복"
    phone    = "0101111$Run"
    userType = "ADULT"
})
Test-Case "4. 전화번호 중복 가입 거부" $r 409

# 5. 로그인 (성인) → 200
$r = Send-Api -Method "POST" -Path "/api/auth/login" -Body ([ordered]@{
    email    = $adultEmail
    password = "1234"
})
Test-Case "5. 로그인" $r 200
$loginToken = Get-Token $r
if ($loginToken) { $adultToken = $loginToken }

# 6. 보호자 등록 (성인, 토큰 사용) → 200
$r = Send-Api -Method "POST" -Path "/api/guardians" -Token $adultToken -Body ([ordered]@{
    name         = "이친구"
    phone        = "010-6666-$Run"
    relationship = "ACQUAINTANCE"
})
Test-Case "6. 보호자 등록" $r 200
$adultGuardianId = $null
if ($r.Body -and $r.Body.data) { $adultGuardianId = $r.Body.data.guardianId }

# 7. 보호자 목록 조회 (성인) → 200, 1명
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $adultToken
Test-Case "7. 보호자 목록 조회 (1명)" $r 200 {
    param($res)
    $count = @($res.Body.data).Count
    if ($count -eq 1) { $true } else { "보호자 수가 1이 아니라 $count" }
}

# 7-1. 보호자 목록 조회 (미성년자) → 200, 2명 + 정규화 확인
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
$kidGuardianIds = @()
if ($r.Body -and $r.Body.data) { $kidGuardianIds = @(@($r.Body.data) | ForEach-Object { $_.guardianId }) }
Test-Case "7-1. 가입 시 보호자 저장 확인 (2명, 전화번호·관계 정규화)" $r 200 {
    param($res)
    $list = @($res.Body.data)
    if ($list.Count -ne 2) { return "보호자 수가 2가 아니라 $($list.Count)" }
    if ($list[0].phone -ne "010-3333-$Run") { return "전화번호 정규화 실패: $($list[0].phone)" }
    if ($list[1].relationship -ne "FAMILY") { return "한글 관계(가족)가 FAMILY로 변환되지 않음: $($list[1].relationship)" }
    $true
}

# 7-2. 내 정보 조회 → 200, 비밀번호 미포함, 전화번호 정규화
$r = Send-Api -Method "GET" -Path "/api/users/me" -Token $kidToken
Test-Case "7-2. 내 정보 조회" $r 200 {
    param($res)
    $names = $res.Body.data.PSObject.Properties.Name
    if ($names -contains "password") { return "응답에 password가 포함됨" }
    if ($res.Body.data.phone -ne "010-2222-$Run") { return "전화번호 정규화 실패: $($res.Body.data.phone)" }
    if ($res.Body.data.userType -ne "MINOR") { return "userType이 MINOR가 아님: $($res.Body.data.userType)" }
    $true
}

# 7-3. 잘못된 관계 값 → 400
$r = Send-Api -Method "POST" -Path "/api/guardians" -Token $adultToken -Body ([ordered]@{
    name         = "이웃"
    phone        = "010-7777-$Run"
    relationship = "NEIGHBOR"
})
Test-Case "7-3. 잘못된 관계 값 거부" $r 400

# 7-4. 같은 보호자 중복 등록 (6번과 같은 번호) → 409
$r = Send-Api -Method "POST" -Path "/api/guardians" -Token $adultToken -Body ([ordered]@{
    name         = "이친구"
    phone        = "0106666$Run"
    relationship = "ACQUAINTANCE"
})
Test-Case "7-4. 보호자 중복 등록 거부" $r 409

# 8. 토큰 없이 보호자 조회 → 401
$r = Send-Api -Method "GET" -Path "/api/guardians"
Test-Case "8. 토큰 없이 요청 거부" $r 401

# 9. 위조된 토큰 → 401
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.forged-signature"
Test-Case "9. 위조된 토큰 거부" $r 401

# 10. 토큰 없이 다른 API → 401 (전체 적용 확인)
$r = Send-Api -Method "GET" -Path "/api/routes/safe"
Test-Case "10. 다른 API도 토큰 검사 (/api/routes/safe)" $r 401

# ── 보호자 삭제 ───────────────────────────────────────────────────────
Write-Host ""
Write-Host "보호자 삭제" -ForegroundColor Cyan

# 11. 성인: 보호자 삭제 → 200
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$adultGuardianId" -Token $adultToken
Test-Case "11. 성인 보호자 삭제 (마지막 1명도 삭제 가능)" $r 200

# 12. 삭제 후 목록 → 0명
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $adultToken
Test-Case "12. 삭제 후 목록에서 사라짐 (0명)" $r 200 {
    param($res)
    $count = @($res.Body.data | Where-Object { $_ }).Count
    if ($count -eq 0) { $true } else { "보호자 수가 0이 아니라 $count" }
}

# 13. 이미 삭제된 보호자 다시 삭제 → 404
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$adultGuardianId" -Token $adultToken
Test-Case "13. 이미 삭제된 보호자 삭제 거부" $r 404

# 14. 삭제한 보호자 번호로 다시 등록 → 200
$r = Send-Api -Method "POST" -Path "/api/guardians" -Token $adultToken -Body ([ordered]@{
    name         = "이친구"
    phone        = "010-6666-$Run"
    relationship = "ACQUAINTANCE"
})
Test-Case "14. 삭제한 번호 재등록 허용" $r 200

# 15. 다른 사용자의 보호자 삭제 (성인 토큰으로 미성년자의 보호자) → 404
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$($kidGuardianIds[1])" -Token $adultToken
Test-Case "15. 다른 사용자의 보호자 삭제 거부" $r 404

# 16. 미성년자: 보호자 2명 중 1명 삭제 → 200
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$($kidGuardianIds[1])" -Token $kidToken
Test-Case "16. 미성년자 보호자 삭제 (2명 → 1명)" $r 200

# 17. 미성년자: 마지막 1명 삭제 → 400
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$($kidGuardianIds[0])" -Token $kidToken
Test-Case "17. 미성년자 마지막 보호자 삭제 거부" $r 400

# 18. 미성년자 목록 → 1명 남음
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "18. 미성년자 보호자 1명 유지 확인" $r 200 {
    param($res)
    $list = @($res.Body.data | Where-Object { $_ })
    if ($list.Count -ne 1) { return "보호자 수가 1이 아니라 $($list.Count)" }
    if ($list[0].guardianId -ne $kidGuardianIds[0]) { return "남은 보호자가 다름: $($list[0].guardianId)" }
    $true
}

# 19. 잘못된 ID 형식 → 400
$r = Send-Api -Method "DELETE" -Path "/api/guardians/abc" -Token $kidToken
Test-Case "19. 잘못된 보호자 ID 형식 거부" $r 400

# 20. 토큰 없이 삭제 → 401
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$($kidGuardianIds[0])"
Test-Case "20. 토큰 없이 삭제 거부" $r 401

# ── 대표 보호자 · 보호자 수정 (DB v3 반영) ──────────────────────────────
# 이 시점 미성년자 보호자: $kidGuardianIds[0](김엄마) 1명. 가입 때 첫 보호자라 자동으로 대표다.
$kidMomId = $kidGuardianIds[0]

# 21. 남은 보호자가 대표인지 + 새 필드 확인
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "21. 첫 보호자 자동 대표 지정 · 새 응답 필드" $r 200 {
    param($res)
    $g = @($res.Body.data)[0]
    if ($g.isPrimary -ne $true) { return "isPrimary가 true가 아님: $($g.isPrimary)" }
    if ($g.registrationStatus -ne "UNREGISTERED") { return "registrationStatus: $($g.registrationStatus)" }
    if ($g.canViewLocation -ne $false) { return "canViewLocation: $($g.canViewLocation)" }
    $true
}

# 22. 대표로 지정하며 추가 → 200, 새 보호자가 대표
$r = Send-Api -Method "POST" -Path "/api/guardians" -Token $kidToken -Body ([ordered]@{
    name            = "김아빠"
    phone           = "010-8888-$Run"
    relationship    = "PARENT"
    isPrimary       = $true
    canViewLocation = $true
})
Test-Case "22. 대표로 지정하며 보호자 추가" $r 200 {
    param($res)
    if ($res.Body.data.isPrimary -ne $true) { return "isPrimary가 true가 아님" }
    if ($res.Body.data.canViewLocation -ne $true) { return "canViewLocation이 true가 아님" }
    $true
}
$kidDadId = $null
if ($r.Body -and $r.Body.data) { $kidDadId = $r.Body.data.guardianId }

# 23. 목록: 대표는 1명뿐 (김엄마는 해제됨)
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "23. 대표 보호자는 1명만 유지" $r 200 {
    param($res)
    $primaries = @(@($res.Body.data) | Where-Object { $_.isPrimary -eq $true })
    if ($primaries.Count -ne 1) { return "대표 수가 1이 아니라 $($primaries.Count)" }
    if ($primaries[0].guardianId -ne $kidDadId) { return "대표가 김아빠가 아님: $($primaries[0].name)" }
    $true
}

# 24. 수정: 이름·위치 열람 변경 + 김엄마를 다시 대표로 → 200
$r = Send-Api -Method "PATCH" -Path "/api/guardians/$kidMomId" -Token $kidToken -Body ([ordered]@{
    name            = "엄마"
    isPrimary       = $true
    canViewLocation = $true
})
Test-Case "24. 보호자 수정 (이름·대표·위치 열람)" $r 200 {
    param($res)
    $d = $res.Body.data
    if ($d.name -ne "엄마") { return "이름이 바뀌지 않음: $($d.name)" }
    if ($d.isPrimary -ne $true) { return "대표로 바뀌지 않음" }
    if ($d.canViewLocation -ne $true) { return "canViewLocation이 바뀌지 않음" }
    if ($d.relationship -ne "PARENT") { return "생략한 relationship이 바뀜: $($d.relationship)" }
    $true
}

# 25. 대표 보호자를 직접 해제 → 400
$r = Send-Api -Method "PATCH" -Path "/api/guardians/$kidMomId" -Token $kidToken -Body @{ isPrimary = $false }
Test-Case "25. 대표 보호자 직접 해제 거부" $r 400

# 26. 전화번호 수정 → 400
$r = Send-Api -Method "PATCH" -Path "/api/guardians/$kidDadId" -Token $kidToken -Body @{ phone = "010-9999-$Run" }
Test-Case "26. 보호자 전화번호 수정 거부" $r 400

# 27. 다른 사용자의 보호자 수정 → 404
$r = Send-Api -Method "PATCH" -Path "/api/guardians/$kidDadId" -Token $adultToken -Body @{ name = "남의보호자" }
Test-Case "27. 다른 사용자의 보호자 수정 거부" $r 404

# 28. 대표(엄마) 삭제 → 200
$r = Send-Api -Method "DELETE" -Path "/api/guardians/$kidMomId" -Token $kidToken
Test-Case "28. 대표 보호자 삭제" $r 200

# 29. 목록: 김아빠 1명이 대표 승계
$r = Send-Api -Method "GET" -Path "/api/guardians" -Token $kidToken
Test-Case "29. 대표 삭제 후 남은 보호자가 대표 승계" $r 200 {
    param($res)
    $list = @($res.Body.data | Where-Object { $_ })
    if ($list.Count -ne 1) { return "보호자 수가 1이 아니라 $($list.Count)" }
    if ($list[0].isPrimary -ne $true) { return "남은 보호자가 대표가 아님" }
    $true
}

# 30. 가입 시 대표 2명 지정 → 400
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email     = "kid-2primary$Run@test.com"
    password  = "1234"
    name      = "최둘"
    phone     = "010-1212-$Run"
    userType  = "MINOR"
    guardians = @(
        [ordered]@{ name = "가"; phone = "010-1313-$Run"; relationship = "PARENT"; isPrimary = $true },
        [ordered]@{ name = "나"; phone = "010-1414-$Run"; relationship = "PARENT"; isPrimary = $true }
    )
})
Test-Case "30. 가입 시 대표 보호자 2명 지정 거부" $r 400

# ── 요약 ──────────────────────────────────────────────────────────────
$total = $script:passCount + $script:failCount
Write-Host ""
if ($script:failCount -eq 0) {
    Write-Host "결과: $total개 중 $($script:passCount)개 통과 - 모두 통과했습니다." -ForegroundColor Green
} else {
    Write-Host "결과: $total개 중 $($script:passCount)개 통과, $($script:failCount)개 실패" -ForegroundColor Red
}
Write-Host ""
Write-Host "이번 실행에서 만든 테스트 계정: $adultEmail, $kidEmail" -ForegroundColor DarkGray
Write-Host ""

$client.Dispose()
if ($script:failCount -gt 0) { exit 1 }
