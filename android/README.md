# 안심동행 Android 실행 안내

## 프로젝트 열기

Android Studio에서 이 `android` 폴더를 엽니다. 실행 모듈은 `mobile`입니다.
기존 기능 패키지와 `wear` 폴더는 유지했습니다. 아직 앱 설정이 없는 `wear`는 Gradle 모듈에 포함하지 않았습니다.

- AGP 9.3.2 / Gradle Wrapper 9.5.0 / AGP 내장 Kotlin
- Gradle JDK: 17 이상(현재 PC에서는 Android Studio 내장 JBR 사용)
- Android SDK Platform 37 / Build Tools 36.0.0 / 최소 Android 6.0(API 23)
- Kakao Maps SDK v2: 2.15.2

## Kakao API 키

`local.properties.example`을 `local.properties`로 복사하고 값을 입력합니다.
이미 파일이 있으면 SDK 경로를 유지하고 키 항목만 추가합니다.

```properties
sdk.dir=C\:/Users/YOUR_USER/AppData/Local/Android/Sdk
KAKAO_NATIVE_APP_KEY=카카오에서_발급받은_네이티브_앱_키
KAKAO_REST_API_KEY=카카오에서_발급받은_REST_API_키
```

각 키는 따옴표 없는 32자리 값입니다. 네이티브 앱 키와 REST API 키를 각각 해당 항목에 입력하세요.
REST API 키는 앱 코드에서 `com.safewalk.BuildConfig.KAKAO_REST_API_KEY`로 읽을 수 있습니다.
REST API 키는 선택 사항이며 비워 둬도 빌드할 수 있습니다. 지도 화면에서 장소를 검색하면 카카오 로컬 REST API를 호출합니다. 검색 결과(최대 15개)의 장소를 누르면 해당 좌표로 지도가 이동합니다.
Gradle이 키를 `BuildConfig`에 주입하고 `SafeWalkApplication`에서 SDK를 초기화합니다.
키를 변경하면 앱을 다시 빌드해야 합니다. 키가 비어 있으면 빌드는 가능하며 앱에 설정 안내가 표시됩니다.
`local.properties`, 빌드 결과물, 서명 키 파일은 `.gitignore`에서 제외했습니다.
이 방식은 저장소에 키가 들어가는 것을 방지합니다. 설치 APK에는 키가 포함되므로 카카오의 패키지명/서명 키 해시 제한도 설정해야 합니다.

## Kakao Developers 설정

1. 사용할 앱의 **카카오맵 > 사용 설정 > 상태**를 ON으로 설정합니다.
2. 해당 Native App Key의 Android 앱 정보에 패키지명 `com.safewalk`와 서명 인증서의 **키 해시**를 등록합니다.
3. 개발 PC별 debug 인증서, 배포용 release 인증서, Google Play App Signing 사용 시 Play 앱 서명 인증서의 키 해시를 각각 등록합니다.

키 해시는 인증서 SHA-1 바이트의 Base64 값입니다. `signingReport`의 콜론으로 구분된 SHA-1 문자열 자체와 다릅니다.
첫 debug 빌드 후 아래 PowerShell 명령으로 기본 debug 인증서의 값을 확인할 수 있습니다(별도 OpenSSL 불필요).

```powershell
$keytool = 'C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe'
$certificateFile = Join-Path $env:TEMP ('safewalk-debug-' + [guid]::NewGuid() + '.der')
& $keytool -exportcert -alias androiddebugkey -keystore "$env:USERPROFILE/.android/debug.keystore" -storepass android -file $certificateFile
if ($LASTEXITCODE -ne 0) { throw '인증서 내보내기 실패' }
$sha1 = [System.Security.Cryptography.SHA1]::Create()
try {
    [Convert]::ToBase64String($sha1.ComputeHash([System.IO.File]::ReadAllBytes($certificateFile)))
} finally {
    $sha1.Dispose()
    Remove-Item -LiteralPath $certificateFile
}
```

Release 인증서는 위 명령의 keystore와 alias를 실제 값으로 바꾸고 `-storepass android`를 제거해 비밀번호를 직접 입력합니다.
Play 앱 서명 인증서는 Play Console에서 다운로드한 DER 인증서에 같은 SHA-1/Base64 계산을 적용합니다.

## 빌드와 확인

`android` 폴더에서 실행합니다. Windows 기본 Java가 8이라면 아래처럼 현재 셸에 JBR을 지정합니다.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :mobile:assembleDebug :mobile:lintDebug
```

APK: `mobile/build/outputs/apk/debug/mobile-debug.apk`

1. 인터넷 연결이 있는 ARM64 실제 기기 또는 ARM 호환 에뮬레이터에서 `mobile` 실행.
2. 키/해시/활성화 설정 후 서울시청 중심 기본 지도가 표시되는지 확인.
3. 지도 이동/확대, 홈으로 이동 후 복귀, 화면 회전 후 지도가 정상인지 확인.
4. 인증 오류는 Logcat의 `k3f` 필터로 확인. 401은 키/패키지명/키 해시, 403은 사용 설정 및 권한 확인.

SDK 공식 지원 ABI는 `armeabi-v7a`, `arm64-v8a`이며 OpenGL ES 2.0 이상이 필요합니다.
일반적인 x86 전용 에뮬레이터에서는 실행할 수 없습니다.
기본 지도에는 위치 권한이 필요하지 않아 위치 권한 요청을 넣지 않았습니다.
향후 현재 위치 기능을 추가할 때 위치 권한과 런타임 요청을 함께 구현해야 합니다.
Release 빌드는 난독화 검증용 unsigned APK를 생성하며 배포 서명은 별도 설정이 필요합니다.

## 추가 파일

- `.gitignore`(저장소 루트): 로컬 키와 생성물 제외
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`: 프로젝트 및 SDK 저장소 설정
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: Gradle 실행 환경
- `local.properties.example`: 개인 설정 템플릿
- `mobile/build.gradle.kts`, `mobile/proguard-rules.pro`: 앱, 의존성, 키 주입 및 난독화 규칙
- `mobile/src/main/AndroidManifest.xml`: 앱 초기화 클래스, 시작 화면, 인터넷 권한, OpenGL 요구사항
- `mobile/src/main/java/com/safewalk/common/SafeWalkApplication.kt`: SDK 초기화
- `mobile/src/main/java/com/safewalk/map/MapActivity.kt`: 지도와 생명주기 처리
- `mobile/src/main/res/layout/activity_map.xml`, `res/values/strings.xml`, `res/values/themes.xml`: 화면 리소스

## 공식 문서

- [Kakao Maps 시작하기](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/)
- [SDK 요구사항](https://apis.map.kakao.com/android_v2/docs/getting-started/)
- [카카오맵 사용 설정](https://developers.kakao.com/docs/ko/kakaomap/common)
- [키 해시 안내](https://developers.kakao.com/docs/ko/android/getting-started)
- [AGP 9.3 호환성](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
