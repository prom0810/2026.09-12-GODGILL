# ─────────────────────────────────────────────────────────────────────
# FCM 토큰 등록·해제 API + 테스트 푸시 자동 테스트 (PowerShell)
#
# 실행 방법 (백엔드를 먼저 실행해 둔 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\device-push-test.ps1
#
# 테스트 푸시(22~25번)는 .env에 DEV_PUSH_TEST_ENABLED=true가 있어야 실행된다 (없으면 SKIP).
# 가짜 토큰을 쓰므로 실제 폰에는 알림이 가지 않는다. 대신 FCM 모드에서는
#   - 서버의 Firebase 인증(서비스 계정 키)이 정상인지
#   - FCM이 "잘못된 토큰"이라고 답하면 그 기기를 자동으로 비활성화하는지
# 를 확인한다. 실제 폰 도착 확인은 앱이 FCM 토큰을 등록한 뒤 /api/dev/push-test를 호출하면 된다.
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


$script:skipCount = 0
function Skip-Case([string]$Name, [string]$Why) {
    $script:skipCount++
    Write-Host "[SKIP] " -ForegroundColor DarkYellow -NoNewline
    Write-Host "$Name - $Why"
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

$aEmail = "devicea$Run@test.com"
$bEmail = "deviceb$Run@test.com"
# 실제 FCM 토큰처럼 길고 무작위인 가짜 토큰
$fakeToken = "test-fcm-token-$Run-" + [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")

# 1~2. 테스트 계정 2개 (같은 폰에서 계정을 바꿔 로그인하는 상황용)
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = $aEmail; password = "1234"; name = "기기에이"; phone = "010-7100-$Run"; userType = "ADULT"
})
Test-Case "1. 계정 A 가입" $r 200
$aToken = Get-Token $r

$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = $bEmail; password = "1234"; name = "기기비"; phone = "010-7200-$Run"; userType = "ADULT"
})
Test-Case "2. 계정 B 가입" $r 200
$bToken = Get-Token $r

# ── 입력 검사 ──────────────────────────────────────────────────────────
$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken }
Test-Case "3. 토큰(JWT) 없이 기기 등록 → 401" $r 401

$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = "   " } -Token $aToken
Test-Case "4. 빈 fcmToken → 400" $r 400

$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = ("x" * 513) } -Token $aToken
Test-Case "5. 513자 fcmToken → 400" $r 400

$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken; deviceType = "IPHONE" } -Token $aToken
Test-Case "6. 없는 deviceType → 400" $r 400

$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Token $aToken
Test-Case "7. 본문 없이 등록 → 400" $r 400

# ── 등록 · 재등록 · 계정 전환 ───────────────────────────────────────────
$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken; deviceType = "ANDROID" } -Token $aToken
Test-Case "8. A가 기기 등록" $r 200 {
    param($res)
    $d = $res.Body.data
    if ($d.active -ne $true) { return "active가 true가 아님" }
    if ($d.deviceType -ne "ANDROID") { return "deviceType: $($d.deviceType)" }
    $true
}
$deviceId = $r.Body.data.userDeviceId

$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = " $fakeToken " } -Token $aToken
Test-Case "9. 같은 토큰 재등록(앞뒤 공백, 종류 생략) → 같은 기기 유지" $r 200 {
    param($res)
    $d = $res.Body.data
    if ($d.userDeviceId -ne $deviceId) { return "새 행이 만들어짐: $($d.userDeviceId)" }
    if ($d.deviceType -ne "ANDROID") { return "deviceType이 바뀜: $($d.deviceType)" }
    if (-not $d.updatedAt) { return "updatedAt이 비어 있음" }
    $true
}

$r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken } -Token $bToken
Test-Case "10. 같은 폰에서 B로 로그인 → 같은 기기, 주인만 B로" $r 200 {
    param($res)
    if ($res.Body.data.userDeviceId -ne $deviceId) { "새 행이 만들어짐: $($res.Body.data.userDeviceId)" } else { $true }
}

$r = Send-Api -Method "DELETE" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken } -Token $aToken
Test-Case "11. A가 (이제 B의) 토큰 해제 요청 → 200, 아무 변화 없음" $r 200

$r = Send-Api -Method "DELETE" -Path "/api/users/me/devices" -Body @{ fcmToken = "no-such-token-$Run" } -Token $aToken
Test-Case "12. 없는 토큰 해제 → 200" $r 200

$r = Send-Api -Method "DELETE" -Path "/api/users/me/devices" -Token $aToken
Test-Case "13. 본문 없이 해제 → 400" $r 400

$r = Send-Api -Method "DELETE" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken }
Test-Case "14. 토큰(JWT) 없이 해제 → 401" $r 401

# ── 테스트 푸시 ────────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/dev/push-test" -Body @{} -Token $bToken
if ($r.Status -eq 404) {
    Skip-Case "20~25. 테스트 푸시" ".env에 DEV_PUSH_TEST_ENABLED=true를 넣고 서버를 다시 실행하면 확인할 수 있습니다."
} else {
    $mode = $r.Body.data.mode
    Write-Host "       푸시 발송 방식: $mode" -ForegroundColor Cyan

    # A는 기기가 없음 (10번에서 B로 옮겨감)
    $ra = Send-Api -Method "POST" -Path "/api/dev/push-test" -Body @{} -Token $aToken
    Test-Case "20. 기기 없는 A에게 테스트 푸시 → 0대" $ra 200 {
        param($res)
        if ($res.Body.data.deviceCount -eq 0) { $true } else { "deviceCount: $($res.Body.data.deviceCount)" }
    }

    if ($mode -eq "CONSOLE") {
        Test-Case "21. B에게 테스트 푸시 (콘솔) → 1대 성공, 서버 로그에 출력" $r 200 {
            param($res)
            $d = $res.Body.data
            if ($d.deviceCount -ne 1) { return "deviceCount: $($d.deviceCount)" }
            if ($d.successCount -ne 1) { return "successCount: $($d.successCount)" }
            $true
        }
        Skip-Case "22~23. FCM 인증·토큰 정리" "콘솔 모드. .env에 FIREBASE_CREDENTIALS_PATH를 넣으면 확인할 수 있습니다."
    } else {
        Test-Case "21. B에게 테스트 푸시 (FCM, 가짜 토큰) → 1대 시도" $r 200 {
            param($res)
            if ($res.Body.data.deviceCount -eq 1) { $true } else { "deviceCount: $($res.Body.data.deviceCount)" }
        }
        Test-Case "22. Firebase 서버 인증 정상 (가짜 토큰만 거절됨)" $r 200 {
            param($res)
            $d = $res.Body.data
            $codes = @($d.errorCodes)
            $tokenErrors = @("INVALID_ARGUMENT", "UNREGISTERED", "SENDER_ID_MISMATCH")
            $other = @($codes | Where-Object { $tokenErrors -notcontains $_ })
            if ($other.Count -gt 0) {
                return "토큰이 아닌 다른 원인으로 실패: $($other -join ', ') - 서비스 계정 키, Firebase Cloud Messaging API(V1) 사용 설정을 확인하세요."
            }
            if ($d.deactivatedCount -ne 1) { return "가짜 토큰이 비활성화되지 않음 (deactivatedCount: $($d.deactivatedCount), errorCodes: $($codes -join ', '))" }
            $true
        }
        $r = Send-Api -Method "POST" -Path "/api/dev/push-test" -Body @{} -Token $bToken
        Test-Case "23. 비활성화된 토큰에는 다시 보내지 않음 → 0대" $r 200 {
            param($res)
            if ($res.Body.data.deviceCount -eq 0) { $true } else { "deviceCount: $($res.Body.data.deviceCount)" }
        }
    }

    # 로그아웃 → 해제 → 0대
    $r = Send-Api -Method "PUT" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken } -Token $bToken
    Test-Case "24. B 재등록 → 다시 활성화" $r 200 {
        param($res)
        if ($res.Body.data.active -eq $true) { $true } else { "active: $($res.Body.data.active)" }
    }
    $null = Send-Api -Method "DELETE" -Path "/api/users/me/devices" -Body @{ fcmToken = $fakeToken } -Token $bToken
    $r = Send-Api -Method "POST" -Path "/api/dev/push-test" -Body @{} -Token $bToken
    Test-Case "25. B 로그아웃(해제) 후 테스트 푸시 → 0대" $r 200 {
        param($res)
        if ($res.Body.data.deviceCount -eq 0) { $true } else { "deviceCount: $($res.Body.data.deviceCount)" }
    }
}

# ── 요약 ──────────────────────────────────────────────────────────────
$total = $script:passCount + $script:failCount
Write-Host ""
if ($script:failCount -eq 0) {
    Write-Host "결과: $total개 중 $($script:passCount)개 통과 - 모두 통과했습니다. (SKIP $($script:skipCount)개)" -ForegroundColor Green
} else {
    Write-Host "결과: $total개 중 $($script:passCount)개 통과, $($script:failCount)개 실패 (SKIP $($script:skipCount)개)" -ForegroundColor Red
}
Write-Host ""
Write-Host "이번 실행에서 만든 테스트 계정: $aEmail, $bEmail" -ForegroundColor DarkGray
Write-Host ""

$client.Dispose()
if ($script:failCount -gt 0) { exit 1 }
