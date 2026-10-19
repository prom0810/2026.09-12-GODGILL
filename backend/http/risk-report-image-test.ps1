# ─────────────────────────────────────────────────────────────────────
# 위험 제보 + 제보 사진(이미지 저장소) API 자동 테스트 (PowerShell)
#
# 실행 방법 (백엔드를 먼저 실행해 둔 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\risk-report-image-test.ps1
#
# .env의 STORAGE_TYPE에 따라 Supabase Storage(supabase) 또는 로컬 폴더(local)에 실제로 파일이 올라간다.
# 사진 URL로 다시 내려받아 원본과 같은지, 삭제 후에는 내려받을 수 없는지까지 확인한다.
#
# 매 실행마다 테스트 계정 2개와 제보 2건이 DB에 남는다 (사진은 테스트 중 일부 삭제됨).
# ─────────────────────────────────────────────────────────────────────

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(60)

$Run = Get-Random -Minimum 1000 -Maximum 9999
$script:passCount = 0
$script:failCount = 0

# ── 테스트용 이미지 ─────────────────────────────────────────────────────
# 1x1 픽셀 PNG (실제로 열리는 이미지)
$PngBytes = [Convert]::FromBase64String(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
# 이미지가 아닌 파일 (확장자와 Content-Type만 jpg로 위장)
$FakeBytes = [System.Text.Encoding]::UTF8.GetBytes("this is not an image")

# ── 응답 정리 ───────────────────────────────────────────────────────────
function Read-Response($response) {
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

# ── JSON 요청 ───────────────────────────────────────────────────────────
function Send-Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
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
    return Read-Response ($client.SendAsync($request).GetAwaiter().GetResult())
}

# ── multipart/form-data 요청 ────────────────────────────────────────────
# $Fields: 텍스트 필드 (hashtable)
# $Files : @( @{ Name="images"; FileName="a.png"; ContentType="image/png"; Bytes=[byte[]] }, ... )
function Send-Multipart {
    param([string]$Method, [string]$Path, $Fields = @{}, $Files = @(), [string]$Token = $null)
    $request = New-Object System.Net.Http.HttpRequestMessage(
        (New-Object System.Net.Http.HttpMethod($Method)), "$BaseUrl$Path")
    if ($Token) {
        $request.Headers.Authorization =
            New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    }
    $content = New-Object System.Net.Http.MultipartFormDataContent
    foreach ($key in $Fields.Keys) {
        $part = New-Object System.Net.Http.StringContent([string]$Fields[$key], [System.Text.Encoding]::UTF8)
        $part.Headers.ContentType = $null
        $content.Add($part, $key)
    }
    foreach ($f in $Files) {
        $part = New-Object System.Net.Http.ByteArrayContent(,[byte[]]$f.Bytes)
        $part.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($f.ContentType)
        $content.Add($part, $f.Name, $f.FileName)
    }
    $request.Content = $content
    return Read-Response ($client.SendAsync($request).GetAwaiter().GetResult())
}

# ── URL로 파일 내려받기 (인증 헤더 없이) ─────────────────────────────────
function Get-Url([string]$Url) {
    $response = $client.GetAsync($Url).GetAwaiter().GetResult()
    $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Bytes = $bytes }
}

function Test-SameBytes([byte[]]$a, [byte[]]$b) {
    if ($a.Length -ne $b.Length) { return $false }
    for ($i = 0; $i -lt $a.Length; $i++) { if ($a[$i] -ne $b[$i]) { return $false } }
    return $true
}

function Png([string]$FileName = "photo.png") {
    return @{ Name = "images"; FileName = $FileName; ContentType = "image/png"; Bytes = $PngBytes }
}

# ── 결과 판정 ───────────────────────────────────────────────────────────
function Test-Case {
    param([string]$Name, $Result, [int]$Expected, [scriptblock]$Extra = $null)
    $ok = ($Result.Status -eq $Expected)
    $reason = ""
    if ($ok -and $Extra) {
        $check = & $Extra $Result
        if ($check -ne $true) { $ok = $false; $reason = [string]$check }
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
        if ($Result.Raw) { Write-Host "       응답: $($Result.Raw)" -ForegroundColor DarkGray }
    }
}

function Get-Token($Result) {
    if ($Result.Body -and $Result.Body.data) { return $Result.Body.data.accessToken }
    return $null
}

function Copy-Fields($Fields) {
    $copy = [ordered]@{}
    foreach ($key in $Fields.Keys) { $copy[$key] = $Fields[$key] }
    return $copy
}

function Image-Count($Result) {
    return @($Result.Body.data.images | Where-Object { $_ }).Count
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

# 0. 테스트 계정 2개 (작성자, 다른 사용자)
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = "reporter$Run@test.com"; password = "1234"; name = "제보자"
    phone = "010-7777-$Run"; userType = "ADULT"
})
Test-Case "0-1. 작성자 가입" $r 200
$token = Get-Token $r

$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{
    email = "other$Run@test.com"; password = "1234"; name = "다른사람"
    phone = "010-8888-$Run"; userType = "ADULT"
})
Test-Case "0-2. 다른 사용자 가입" $r 200
$otherToken = Get-Token $r

if (-not $token -or -not $otherToken) {
    Write-Host "테스트 계정을 만들지 못해 중단합니다." -ForegroundColor Red
    exit 1
}

$baseFields = [ordered]@{
    riskType    = "DARK_ROAD"
    lat         = "36.8090"
    lng         = "127.1470"
    address     = "천안역 동부광장 인근"
    description = "가로등이 꺼져 있음 (테스트 $Run)"
}

# 1. 사진 없이 제보 작성 → 200, 사진 0장
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Token $token -Fields $baseFields
Test-Case "1. 사진 없이 제보 작성" $r 200 {
    param($res)
    if ((Image-Count $res) -ne 0) { return "사진 수가 0이 아님" }
    if ($res.Body.data.status -ne "PENDING") { return "status가 PENDING이 아님: $($res.Body.data.status)" }
    $true
}

# 2. 사진 2장과 함께 제보 작성 → 200, 사진 2장 + URL
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Token $token -Fields $baseFields `
    -Files @((Png "a.png"), (Png "b.png"))
Test-Case "2. 사진 2장과 함께 제보 작성" $r 200 {
    param($res)
    if ((Image-Count $res) -ne 2) { return "사진 수가 2가 아니라 $(Image-Count $res)" }
    if (-not $res.Body.data.images[0].url) { return "사진 url이 비어 있음" }
    $true
}
$reportId = $r.Body.data.reportId

# 3. 제보 조회 → 200, 사진 2장
$r = Send-Api -Method "GET" -Path "/api/risk-reports/$reportId" -Token $token
Test-Case "3. 제보 조회" $r 200 {
    param($res)
    if ((Image-Count $res) -ne 2) { return "사진 수가 2가 아니라 $(Image-Count $res)" }
    $true
}
$firstImage = $r.Body.data.images[0]

# 4. 사진 URL로 내려받기 → 200, 원본과 동일 (저장소에 실제로 저장됐는지 확인)
$dl = Get-Url $firstImage.url
$dlResult = [pscustomobject]@{ Status = $dl.Status; Body = $null; Raw = "" }
Test-Case "4. 사진 URL로 내려받아 원본과 비교" $dlResult 200 {
    param($res)
    if (-not (Test-SameBytes $dl.Bytes $PngBytes)) { return "내려받은 파일이 원본과 다름 ($($dl.Bytes.Length) bytes)" }
    $true
}

# 5. 사진 1장 추가 → 200, 총 3장
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports/$reportId/images" -Token $token -Files @((Png "c.png"))
Test-Case "5. 사진 1장 추가 (총 3장)" $r 200 {
    param($res)
    if ((Image-Count $res) -ne 3) { return "사진 수가 3이 아니라 $(Image-Count $res)" }
    $true
}

# 6. 최대 장수 초과 → 400
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports/$reportId/images" -Token $token -Files @((Png "d.png"))
Test-Case "6. 사진 4장째 추가 거부" $r 400

# 7. 이미지가 아닌 파일 → 400
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Token $token -Fields $baseFields `
    -Files @(@{ Name = "images"; FileName = "fake.jpg"; ContentType = "image/jpeg"; Bytes = $FakeBytes })
Test-Case "7. 이미지가 아닌 파일 거부" $r 400

# 8. 10MB 초과 사진 → 413
# 서버가 크기 초과를 감지하면 업로드가 끝나기 전에 연결을 끊을 수 있다.
# 이 경우 응답 대신 연결 오류가 나므로, 연결이 끊긴 것도 "거부됨"으로 본다.
$big = New-Object byte[] (11MB)
[Array]::Copy($PngBytes, $big, $PngBytes.Length)
try {
    $r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Token $token -Fields $baseFields `
        -Files @(@{ Name = "images"; FileName = "big.png"; ContentType = "image/png"; Bytes = $big })
    Test-Case "8. 10MB 초과 사진 거부" $r 413
} catch {
    $script:passCount++
    Write-Host "[PASS] " -ForegroundColor Green -NoNewline
    Write-Host "8. 10MB 초과 사진 거부 (서버가 업로드 도중 연결을 끊음)"
}
$big = $null

# 9. 잘못된 위험 유형 → 400
$bad = Copy-Fields $baseFields; $bad.riskType = "UFO"
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Token $token -Fields $bad
Test-Case "9. 잘못된 위험 유형 거부" $r 400

# 10. 위도 범위 밖 → 400
$bad = Copy-Fields $baseFields; $bad.lat = "123.0"
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Token $token -Fields $bad
Test-Case "10. 위도 범위 밖 거부" $r 400

# 11. 필수 값(lng) 누락 → 400
$bad = Copy-Fields $baseFields; $bad.Remove("lng")
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Token $token -Fields $bad
Test-Case "11. 필수 값(lng) 누락 거부" $r 400

# 12. 다른 사용자가 조회 → 404
$r = Send-Api -Method "GET" -Path "/api/risk-reports/$reportId" -Token $otherToken
Test-Case "12. 다른 사용자의 제보 조회 거부" $r 404

# 13. 다른 사용자가 사진 추가 → 404
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports/$reportId/images" -Token $otherToken -Files @((Png "x.png"))
Test-Case "13. 다른 사용자의 제보에 사진 추가 거부" $r 404

# 14. 다른 사용자가 사진 삭제 → 404
$r = Send-Api -Method "DELETE" -Path "/api/risk-reports/$reportId/images/$($firstImage.imageId)" -Token $otherToken
Test-Case "14. 다른 사용자의 제보 사진 삭제 거부" $r 404

# 15. 사진 삭제 → 200, 2장 남음
$r = Send-Api -Method "DELETE" -Path "/api/risk-reports/$reportId/images/$($firstImage.imageId)" -Token $token
Test-Case "15. 사진 1장 삭제 (2장 남음)" $r 200 {
    param($res)
    if ((Image-Count $res) -ne 2) { return "사진 수가 2가 아니라 $(Image-Count $res)" }
    if (@($res.Body.data.images | Where-Object { $_.imageId -eq $firstImage.imageId }).Count -ne 0) {
        return "삭제한 사진이 목록에 남아 있음"
    }
    $true
}

# 16. 삭제한 사진은 저장소에서도 지워졌는지 → 내려받기 실패(200이 아님)
Start-Sleep -Milliseconds 500
$dl = Get-Url $firstImage.url
$dlResult = [pscustomobject]@{ Status = $dl.Status; Body = $null; Raw = "" }
if ($dl.Status -ne 200) {
    $script:passCount++
    Write-Host "[PASS] " -ForegroundColor Green -NoNewline
    Write-Host "16. 삭제한 사진이 저장소에서도 삭제됨 (HTTP $($dl.Status))"
} else {
    $script:failCount++
    Write-Host "[FAIL] " -ForegroundColor Red -NoNewline
    Write-Host "16. 삭제한 사진이 저장소에 아직 남아 있음 (HTTP 200)"
}

# 17. 이미 삭제한 사진 다시 삭제 → 404
$r = Send-Api -Method "DELETE" -Path "/api/risk-reports/$reportId/images/$($firstImage.imageId)" -Token $token
Test-Case "17. 이미 삭제한 사진 삭제 거부" $r 404

# 18. 사진 추가 요청에 파일 없음 → 400
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports/$reportId/images" -Token $token -Fields @{ note = "x" }
Test-Case "18. 파일 없는 사진 추가 요청 거부" $r 400

# 19. 토큰 없이 작성 → 401
$r = Send-Multipart -Method "POST" -Path "/api/risk-reports" -Fields $baseFields
Test-Case "19. 토큰 없이 제보 작성 거부" $r 401

# ── 요약 ──────────────────────────────────────────────────────────────
$total = $script:passCount + $script:failCount
Write-Host ""
if ($script:failCount -eq 0) {
    Write-Host "결과: $total개 중 $($script:passCount)개 통과 - 모두 통과했습니다." -ForegroundColor Green
} else {
    Write-Host "결과: $total개 중 $($script:passCount)개 통과, $($script:failCount)개 실패" -ForegroundColor Red
}
Write-Host ""
Write-Host "이번 실행에서 만든 테스트 계정: reporter$Run@test.com, other$Run@test.com (제보 ID: $reportId 외 1건)" -ForegroundColor DarkGray
Write-Host ""

$client.Dispose()
if ($script:failCount -gt 0) { exit 1 }
