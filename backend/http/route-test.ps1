# ─────────────────────────────────────────────────────────────────────
# 경로 안내 기록 API 자동 테스트 (PowerShell)
#
# 실행 방법 (백엔드를 먼저 실행해 둔 상태에서, backend 폴더 기준):
#   powershell -ExecutionPolicy Bypass -File .\http\route-test.ps1
#
# 확인하는 것
#   - 안내 시작(경로·지점·구간·시설 저장), 진행 중 경로, 상세, 목록
#   - 새 안내를 시작하면 기존 진행 중 경로 자동 취소
#   - 도착(COMPLETED) / 취소(CANCELLED), 이미 끝난 경로 재종료 409
#   - 남의 경로 404, 잘못된 요청 400, 토큰 없음 401
#   - 지점 300개 경로 저장 시간 (ID 선할당 + batch INSERT)
#
# 보관 기간 정리(65일 상세 정리 / 365일 삭제)는 날짜가 지나야 해서 이 스크립트로는 확인하지 않는다.
# 매 실행마다 테스트 계정 2개와 경로 몇 건이 DB에 남는다.
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


# ── 테스트용 경로 만들기 ─────────────────────────────────────────────────
# 천안역에서 북쪽으로 Count개 지점 (지점 간격 약 11m). 첫 구간에 CCTV 1개, 둘째 구간은 위험구역 감점 0.42
function New-RouteBody([int]$Count = 3, [string]$StartName = "천안역", [string]$EndName = "천안시청") {
    $points = @()
    $segments = @()
    for ($i = 0; $i -lt $Count; $i++) {
        $p = [ordered]@{ lat = 36.8100 + $i * 0.0001; lng = 127.1467 }
        if ($i -eq 0) { $p.name = $StartName; $p.address = "충남 천안시 동남구 대흥로 239" }
        if ($i -eq $Count - 1) { $p.name = $EndName }
        if ($i -gt 0 -and $i -lt $Count - 1) { $p.name = "경유지 이름은 저장 안 됨" }
        $points += $p
        if ($i -lt $Count - 1) {
            $facilities = @()
            if ($i -eq 0) {
                $facilities = @([ordered]@{ type = "CCTV"; lat = 36.81005; lng = 127.14675; quantity = 2; distance = 6.2; appliedScore = 0.144 })
            }
            $risk = 0.0
            if ($i -eq 1) { $risk = 0.42 }
            $segments += [ordered]@{ safetyScore = 0.7; riskScore = $risk; facilities = $facilities }
        }
    }
    return [ordered]@{ estimatedTime = 600; safetyScore = 0.7; points = $points; segments = $segments }
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

$emails = @("route-user$Run@test.com", "route-other$Run@test.com")

# ── 준비: 계정 2개 ─────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[0]; password = "1234"; name = "길안심"; phone = "010-9400-$Run"; userType = "ADULT" })
Test-Case "1. 사용자 가입" $r 200
$userToken = Get-Token $r
$r = Send-Api -Method "POST" -Path "/api/auth/signup" -Body ([ordered]@{ email = $emails[1]; password = "1234"; name = "남남"; phone = "010-9500-$Run"; userType = "ADULT" })
Test-Case "2. 다른 사용자 가입" $r 200
$otherToken = Get-Token $r

# ── 안내 시작 ─────────────────────────────────────────────────────────
$r = Send-Api -Method "GET" -Path "/api/routes/active" -Token $userToken
Test-Case "3. 진행 중 경로 없음 → data null" $r 200 { param($res) if ($null -eq $res.Body.data) { $true } else { "data: $($res.Raw)" } }

$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body (New-RouteBody -Count 3)
Test-Case "4. 안내 시작 (지점 3 · 구간 2 · 시설 1)" $r 200 {
    param($res)
    $d = $res.Body.data
    if ($d.status -ne "IN_PROGRESS") { return "status: $($d.status)" }
    if ($d.pointCount -ne 3 -or $d.segmentCount -ne 2 -or $d.facilityCount -ne 1) { return "개수: $($d.pointCount)/$($d.segmentCount)/$($d.facilityCount)" }
    if ($d.totalDistance -lt 20 -or $d.totalDistance -gt 25) { return "총거리(서버 계산, 약 22m): $($d.totalDistance)" }
    if ($null -ne $d.cancelledRouteId) { return "취소된 경로가 없어야 함: $($d.cancelledRouteId)" }
    $true
}
$route1 = $r.Body.data.routeId

$r = Send-Api -Method "GET" -Path "/api/routes/active" -Token $userToken
Test-Case "5. 진행 중 경로 = 방금 시작한 경로, 출발·도착 이름" $r 200 {
    param($res)
    $d = $res.Body.data
    if ($d.routeId -ne $route1) { return "routeId: $($d.routeId)" }
    if ($d.start.name -ne "천안역" -or $d.end.name -ne "천안시청") { return "출발/도착: $($d.start.name) / $($d.end.name)" }
    if ($d.userType -ne "ADULT") { return "userType: $($d.userType)" }
    $true
}

$r = Send-Api -Method "GET" -Path "/api/routes/$route1" -Token $userToken
Test-Case "6. 상세: 지점 종류·구간 점수·시설" $r 200 {
    param($res)
    $d = $res.Body.data
    if (-not $d.detailAvailable) { return "detailAvailable이 false" }
    $types = ($d.points | ForEach-Object { $_.type }) -join ","
    if ($types -ne "START,WAYPOINT,END") { return "지점 종류: $types" }
    if ($null -ne $d.points[1].name) { return "경유 지점 이름은 저장하지 않아야 함: $($d.points[1].name)" }
    if (@($d.segments).Count -ne 2) { return "구간 수: $(@($d.segments).Count)" }
    $f = @($d.segments[0].facilities)
    if ($f.Count -ne 1 -or $f[0].type -ne "CCTV" -or $f[0].quantity -ne 2) { return "시설: $($d.segments[0].facilities | ConvertTo-Json -Compress)" }
    if ([math]::Abs($d.segments[1].riskScore - 0.42) -gt 1e-9) { return "riskScore: $($d.segments[1].riskScore)" }
    $true
}

# ── 권한 ───────────────────────────────────────────────────────────────
$r = Send-Api -Method "GET" -Path "/api/routes/$route1" -Token $otherToken
Test-Case "7. 남의 경로 상세 → 404" $r 404
$r = Send-Api -Method "PATCH" -Path "/api/routes/$route1" -Token $otherToken -Body @{ status = "COMPLETED" }
Test-Case "8. 남의 경로 도착 처리 → 404" $r 404

# ── 새 안내로 대체 ─────────────────────────────────────────────────────
$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body (New-RouteBody -Count 4 -EndName "신부동")
Test-Case "9. 새 안내 시작 → 기존 경로 자동 취소" $r 200 {
    param($res) if ($res.Body.data.cancelledRouteId -eq $route1) { $true } else { "cancelledRouteId: $($res.Body.data.cancelledRouteId)" }
}
$route2 = $r.Body.data.routeId

$r = Send-Api -Method "GET" -Path "/api/routes/$route1" -Token $userToken
Test-Case "10. 기존 경로 상태 CANCELLED" $r 200 {
    param($res) if ($res.Body.data.summary.status -eq "CANCELLED") { $true } else { "status: $($res.Body.data.summary.status)" }
}

# ── 도착 / 재종료 ─────────────────────────────────────────────────────
$r = Send-Api -Method "PATCH" -Path "/api/routes/$route2" -Token $userToken -Body @{ status = "COMPLETED" }
Test-Case "11. 도착 처리 → COMPLETED, completedAt 기록" $r 200 {
    param($res)
    if ($res.Body.data.status -ne "COMPLETED") { return "status: $($res.Body.data.status)" }
    if (-not $res.Body.data.completedAt) { return "completedAt 없음" }
    $true
}
$r = Send-Api -Method "PATCH" -Path "/api/routes/$route2" -Token $userToken -Body @{ status = "CANCELLED" }
Test-Case "12. 이미 도착한 경로 취소 → 409" $r 409
$r = Send-Api -Method "PATCH" -Path "/api/routes/$route2" -Token $userToken -Body @{ status = "IN_PROGRESS" }
Test-Case "13. status=IN_PROGRESS 요청 → 400" $r 400

$r = Send-Api -Method "GET" -Path "/api/routes/active" -Token $userToken
Test-Case "14. 도착 후 진행 중 경로 없음" $r 200 { param($res) if ($null -eq $res.Body.data) { $true } else { "data: $($res.Raw)" } }

# ── 목록 ───────────────────────────────────────────────────────────────
$r = Send-Api -Method "GET" -Path "/api/routes?page=0&size=10" -Token $userToken
Test-Case "15. 경로 기록 목록 (최신순)" $r 200 {
    param($res)
    $items = @($res.Body.data.items)
    if ($items.Count -ne 2) { return "개수: $($items.Count)" }
    if ($items[0].routeId -ne $route2) { return "첫 항목이 최신 경로가 아님: $($items[0].routeId)" }
    if ($items[0].end.name -ne "신부동") { return "도착 이름: $($items[0].end.name)" }
    if ($res.Body.data.hasNext) { return "hasNext가 true" }
    $true
}
$r = Send-Api -Method "GET" -Path "/api/routes?size=0" -Token $userToken
Test-Case "16. size=0 → 400" $r 400

# ── 잘못된 요청 ───────────────────────────────────────────────────────
$bad = New-RouteBody -Count 3
$bad.segments = @($bad.segments[0])
$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body $bad
Test-Case "17. 구간 수 ≠ 지점 수 − 1 → 400" $r 400

$bad = New-RouteBody -Count 3
$bad.segments[0].facilities[0].type = "BENCH"
$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body $bad
Test-Case "18. 알 수 없는 시설 종류 → 400" $r 400

$bad = New-RouteBody -Count 3
$bad.safetyScore = 1.5
$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body $bad
Test-Case "19. 안전점수 1.5 → 400" $r 400

$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body (New-RouteBody -Count 501)
Test-Case "20. 지점 501개 → 400" $r 400

$r = Send-Api -Method "GET" -Path "/api/routes/active" -Token $userToken
Test-Case "21. 잘못된 요청은 진행 중 경로에 영향 없음" $r 200 { param($res) if ($null -eq $res.Body.data) { $true } else { "data: $($res.Raw)" } }

$r = Send-Api -Method "GET" -Path "/api/routes/active"
Test-Case "22. 토큰 없음 → 401" $r 401
$r = Send-Api -Method "GET" -Path "/api/routes/999999999" -Token $userToken
Test-Case "23. 없는 경로 → 404" $r 404
$r = Send-Api -Method "POST" -Path "/api/emergency/arrival" -Token $userToken
Test-Case "24. 삭제된 스텁 /api/emergency/arrival → 404" $r 404

# ── 실제 크기 경로 저장 시간 ───────────────────────────────────────────
$watch = [System.Diagnostics.Stopwatch]::StartNew()
$r = Send-Api -Method "POST" -Path "/api/routes" -Token $userToken -Body (New-RouteBody -Count 300)
$watch.Stop()
Test-Case "25. 지점 300개 경로 저장 ($($watch.ElapsedMilliseconds) ms)" $r 200 {
    param($res) if ($res.Body.data.pointCount -eq 300 -and $res.Body.data.segmentCount -eq 299) { $true } else { "개수: $($res.Body.data.pointCount)/$($res.Body.data.segmentCount)" }
}
$route3 = $r.Body.data.routeId
$r = Send-Api -Method "PATCH" -Path "/api/routes/$route3" -Token $userToken -Body @{ status = "CANCELLED" }
Test-Case "26. 안내 종료 → CANCELLED, completedAt 없음" $r 200 {
    param($res) if ($res.Body.data.status -eq "CANCELLED" -and -not $res.Body.data.completedAt) { $true } else { "응답: $($res.Raw)" }
}

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
