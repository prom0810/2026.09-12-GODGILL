# 2026.09-12-GODGILL

## 프로젝트 구조

본 저장소는 모바일(안드로이드) 앱, 웨어러블(Wear OS) 앱, 백엔드 서버를 하나의 모노레포로 관리합니다.

| 파트 | 언어 / 프레임워크 |
|---|---|
| `android/` (mobile, wear) | Kotlin, Jetpack Compose |
| `backend/` | **Java 17**, Spring Boot 3.3.4 (Gradle Kotlin DSL 빌드 스크립트 — 애플리케이션 코드는 모두 Java) |

```
safewalk/
├── android/                       # Android Studio에서 이 폴더를 프로젝트로 열기
│   ├── mobile/                    # 모바일 앱 (일반 사용자용)
│   │   └── src/main/java/com/safewalk/
│   │       ├── auth/                # 회원가입·로그인, 사용자 유형 설정, 보호자 연락처 등록
│   │       ├── map/                 # 지도 표시, 안전 경로 표시·추천, 위험도 시각화
│   │       ├── notification/        # 경로 이탈·위험구간 알림, 보호자 알림
│   │       ├── emergency/           # SOS, 가짜 전화, 경보음, 실시간 위치 공유
│   │       ├── companion/           # 웨어러블(wear 모듈)과의 데이터 연동
│   │       └── common/              # 네트워크 클라이언트, DI, 공통 UI/유틸 (여러 화면에서 재사용되는 코드)
│   │
│   └── wear/                      # 웨어러블 앱 (Wear OS)
│       └── src/main/java/com/safewalk/wear/
│           ├── sensor/               # 심박수·걸음 등 센서 데이터 수집
│           ├── falldetect/           # 낙상 감지
│           ├── vitals/               # 생체신호 이상 감지
│           ├── sos/                  # 워치 자체 SOS 트리거
│           └── sync/                 # 모바일 앱으로 데이터 전송 (Data Layer API)
│
└── backend/                       # IntelliJ에서 이 폴더를 프로젝트로 열기
    ├── http/                        # API 테스트 파일 (PowerShell 스크립트, IntelliJ .http)
    └── src/main/java/com/safewalk/
        ├── global/                  # JWT 인증 인터셉터, 예외처리, DB 설정 등 애플리케이션 전역 인프라 코드
        ├── auth/                    # 회원가입/로그인 API, JWT 발급·검증
        ├── user/                    # 사용자 정보, 사용자 유형
        ├── guardian/                # 보호자/지인 추가·조회·삭제
        ├── report/                  # 위험 제보 작성·조회, 제보 사진 추가·삭제
        ├── storage/                 # 이미지 저장소 (Supabase Storage / 로컬 폴더)
        ├── route/                   # 안전 경로 추천 알고리즘, 가중치 적용, 검색
        ├── notification/            # 알림 발송
        ├── emergency/               # SOS, 위치 공유, 도착 확인
        └── datalog/                 # 웨어러블 데이터 수신 및 낙상·생체신호 이상 탐지
```

### 폴더 역할 요약

| 경로 | 역할 |
|---|---|
| `android/mobile` | 일반 사용자가 사용하는 메인 앱 (경로 추천, 긴급 대응 UI 등) |
| `android/wear` | 워치에서 동작하는 센서 수집 전용 앱 |
| `backend` | 경로 추천 알고리즘, 인증, 알림, 데이터 수신을 담당하는 서버 |
| `global` (backend) | 특정 도메인에 속하지 않는 서버 전역 설정·공통 인프라 코드 |
| `common` (android) | 여러 화면에서 재사용되는 클라이언트 공통 코드(네트워크, DI, UI 등) |

> `android/mobile`의 `companion`과 `android/wear`의 `sync`는 짝을 이루는 모듈로, 워치에서 수집된 데이터가 모바일 앱을 거쳐 백엔드의 `datalog`로 전달되는 흐름을 담당합니다.

## 로그인/회원가입 기능 — 2026-09-23 추가

회원가입/로그인 화면(Android)이 실제 백엔드 API(`/api/auth/signup`, `/api/auth/login`)를 호출해
Supabase PostgreSQL의 `users` 테이블에 저장/조회하도록 연결했다. 검증은 의도적으로 최소한만
한다 — 이메일/비밀번호 공백 여부, 이메일 중복(가입 시)/일치(로그인 시)만 확인하며, 이메일
형식이나 비밀번호 길이 같은 세부 규칙은 없다. 사용자 유형(성인/미성년자) 구분은 현재 뼈대만
구현되어 있고 유형별 안전 경로 추천 로직과는 아직 연결되지 않았다.

### 추가/수정된 파일 — android/

| 파일 | 설명 |
|---|---|
| `mobile/src/main/java/com/safewalk/common/network/ApiClient.kt` | 백엔드 HTTP 클라이언트. `baseUrl`에 개발 PC의 로컬 IP가 하드코딩되어 있음 (아래 "다운받아 실행할 때" 참고) |
| `mobile/src/main/java/com/safewalk/auth/AuthApi.kt`, `AuthRepository.kt` | 회원가입/로그인 API 호출(HttpURLConnection 직접 사용) 및 토큰 저장 |
| `mobile/src/main/java/com/safewalk/login/LoginActivity.kt`, `LoginContract.kt`, `LoginScreen.kt`, `LoginViewModel.kt` | 로그인 화면 (API 연동, 로딩/에러 상태 표시, 회원가입 화면 이동 추가) |
| `mobile/src/main/java/com/safewalk/signup/` | 회원가입 화면 (신규) |
| `mobile/src/main/AndroidManifest.xml` | `usesCleartextTraffic="true"` 추가(백엔드가 아직 HTTPS 아님), `SignupActivity` 등록 |
| `mobile/build.gradle.kts` | 로그인/회원가입 API를 비동기 호출하기 위한 코루틴 의존성 추가 |

### 추가/수정된 파일 — backend/

| 파일 | 설명 |
|---|---|
| `src/main/java/com/safewalk/auth/` (`AuthController.java` 등) | 회원가입/로그인 API(`/api/auth/signup`, `/api/auth/login`) 구현 |
| `src/main/resources/application.yml` | `JWT_SECRET` 등 인증 관련 환경변수 참조 추가 |
| `.env.example` | 필요한 환경변수 예시 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`) |

## 백엔드 Java 전환 — 2026-09-29

백엔드 애플리케이션 코드를 Kotlin에서 **Java 17**로 전환했다. Spring Boot 버전, 패키지 구조,
API 경로와 응답 JSON 형식(`{success, data, message}`)은 그대로이므로 Android 쪽 수정은 필요 없다.

- `build.gradle.kts`: Kotlin 플러그인(`kotlin("jvm")`, `plugin.spring`, `plugin.jpa`)과
  `jackson-module-kotlin`, `kotlin-reflect`, `kotlin-test-junit5` 의존성 제거, `java` 플러그인 사용.
  `.env` 자동 로딩 로직은 유지.
- 모든 `.kt` 파일을 같은 경로의 `.java` 파일로 변환. 한 파일에 여러 클래스가 있던
  `AuthDtos.kt` → `SignupRequest` / `LoginRequest` / `AuthResponse`,
  `AuthExceptions.kt` → `DuplicateEmailException` / `InvalidCredentialsException`으로 분리.
- DTO와 `ApiResponse`는 Java `record`, JPA 엔티티(`User`, `UserType`)는 기본 생성자 + getter/setter
  클래스로 작성. Repository의 단건 조회는 `Optional`을 반환한다.

## 로그인/회원가입 개선 · 보호자 기능 — 2026-10-01 (backend)

백엔드에 **JWT 인증 검사**, **회원가입 항목 확장**, **보호자/지인 관리 API**를 추가했다.
Android 쪽도 v3 요청/응답과 보호자 연결 관리 화면까지 반영됐다.

### Android 프론트 반영 완료

1. 회원가입 요청에 `name`, `phone`이 **필수**로 추가됐다. 미성년자(`MINOR`)는 `guardians`도 1명 이상 필수다.
2. `/api/auth/**`를 제외한 **모든 API**에 `Authorization: Bearer {토큰}` 헤더가 필요하다.
   토큰은 로그인/회원가입 응답의 `data.accessToken` (현재 `TokenStore`에 저장 중인 값).
3. 401 응답을 받으면 토큰이 없거나 만료된 것이므로 로그인 화면으로 보낸다.
4. 앱 재실행 시 자동 로그인 확인은 `GET /api/users/me`로 한다 (200 → 메인, 401 → 로그인).

### API

| 메서드 | 경로 | 토큰 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` | - | 회원가입 (+보호자 함께 등록) |
| POST | `/api/auth/login` | - | 로그인 |
| GET | `/api/users/me` | 필요 | 내 정보 (비밀번호 제외) |
| POST | `/api/guardians` | 필요 | 보호자 1명 추가 |
| GET | `/api/guardians` | 필요 | 내 보호자 목록 (등록 순) |
| DELETE | `/api/guardians/{guardianId}` | 필요 | 보호자 1명 삭제 |

회원가입 요청 예시:

```json
POST /api/auth/signup
{
  "email": "kid@test.com",
  "password": "1234",
  "name": "김안심",
  "phone": "010-1234-5678",
  "userType": "MINOR",
  "guardians": [
    { "name": "김엄마", "phone": "010-9876-5432", "relationship": "PARENT" }
  ]
}
```

보호자 응답 예시:

```json
{ "guardianId": 3, "name": "김엄마", "phone": "010-9876-5432",
  "relationship": "PARENT", "relationshipLabel": "부모", "createdAt": "..." }
```

### 규칙

| 구분 | 규칙 |
|---|---|
| 인증 | `/api/**` 전체에 JWT 검사, `/api/auth/**`만 예외. 새 API는 자동으로 검사 대상이 된다. 컨트롤러에서는 `@LoginUser Long userId`로 현재 사용자 ID를 받는다 (요청 바디로 userId를 받지 않음) |
| 회원가입 | email·password·name·phone·userType 필수. 이메일 중복·전화번호 중복 시 409 |
| 전화번호 | 사용자·보호자 모두 `010-1234-5678` 형태로 정규화해서 저장 (`01012345678`, `010 1234 5678`, `+82 10-…` 입력 허용). `users.phone`은 중복 불가 |
| 미성년자 | 가입 시 보호자 1명 이상 필수. **마지막 남은 보호자 1명은 삭제할 수 없음** |
| 보호자 | 사용자당 최대 5명. 관계는 `PARENT`(부모) / `FAMILY`(가족) / `ACQUAINTANCE`(지인) — 한글 값도 입력 가능, 저장은 코드로. 본인 번호 등록 불가, 같은 번호 중복 등록 불가 |
| 삭제 | soft delete (`deleted_at` 기록). 다른 사람의 보호자·없는 ID·이미 삭제된 보호자는 404. 삭제한 번호는 다시 등록 가능 |

### 상태 코드

| 코드 | 의미 |
|---|---|
| 200 | 성공 |
| 400 | 입력 오류 (빈 값, 전화번호 형식, 관계 값, 미성년자 보호자 누락·마지막 보호자 삭제 등) |
| 401 | 토큰 없음·위조·만료, 로그인 실패, 탈퇴한 계정 |
| 404 | 보호자 없음, 없는 API |
| 405 | 지원하지 않는 HTTP 메서드 |
| 409 | 이메일·전화번호·보호자 중복 |

모든 응답은 기존과 같은 `{ "success", "data", "message" }` 형식이며, 오류 메시지는 한글로 내려간다.

### 추가/수정된 파일 — backend/

| 파일 | 설명 |
|---|---|
| `global/AuthInterceptor.java`, `WebConfig.java` | JWT 검사 인터셉터와 적용 범위 설정 |
| `global/LoginUser.java`, `LoginUserArgumentResolver.java` | `@LoginUser`로 컨트롤러에 현재 사용자 ID 주입 |
| `global/PhoneNumbers.java` | 전화번호 정규화 |
| `global/GlobalExceptionHandler.java` | 401·404·405·409 및 JSON 형식 오류 처리 추가 |
| `auth/JwtProvider.java` | 토큰 검증(`parseUserId`) 추가 |
| `auth/AuthService.java`, `SignupRequest.java` | 회원가입 항목 확장, 이름·전화번호 임시값 로직 제거 |
| `auth/UnauthorizedException.java`, `DuplicatePhoneException.java` | 신규 예외 |
| `guardian/` (신규) | `Guardian` 엔티티, Repository, Service, Controller, 요청/응답, `Relationship`, 예외 |
| `user/UserController.java`, `UserService.java`, `UserResponse.java` | `GET /api/users/me` 구현 |
| `user/UserRepository.java`, `UserType.java` | 전화번호 중복 조회, 동시 요청 대비 잠금 조회, `isMinor()` |
| `http/auth-guardian-test.ps1` | PowerShell 자동 테스트 (24개 케이스) |
| `http/auth-guardian.http` | 같은 시나리오의 IntelliJ HTTP Client 파일 (유료 버전에서만 실행 가능) |

### DB 변경 (Supabase에 반영 완료)

- `users.phone`에 UNIQUE 제약 추가
- `guardian` 테이블 사용 시작 (`guardian_id`, `user_id` FK, `name`, `phone`, `relationship`, `created_at`, `deleted_at`)

### 테스트 방법

백엔드를 실행한 상태에서 `backend` 폴더 기준으로 (IntelliJ 터미널 등):

```powershell
powershell -ExecutionPolicy Bypass -File .\http\auth-guardian-test.ps1
```

가입·로그인·토큰 검사·보호자 추가/조회/삭제 24개 케이스를 순서대로 실행하고 `[PASS]`/`[FAIL]`을 출력한다.
실행할 때마다 `@test.com` 테스트 계정이 DB에 생성된다.

### 보류된 기능

- 보호자 정보 수정 API, 보호자 정보 입력 동의 절차
- 아이디/비밀번호 찾기 (3·4차 MVP 예정)
- 웨어러블 기기의 토큰 전달 방식, 계정 없는 보호자용 위치 공유 인증

## 위험 제보 · 제보 사진 저장소 — 2026-10-16 (backend)

사용자가 위험 지점을 사진과 함께 제보하는 API와, 사진을 저장하는 **이미지 저장소**를 추가했다.
사진 파일은 **Supabase Storage**(비공개 버킷 `risk-report-images`)에 저장하고, DB(`risk_report_image.storage_path`)에는
URL이 아니라 버킷 안의 파일 위치(예: `reports/12/3f2a….png`)만 저장한다.
Android 제보 탭에서 다른 사용자의 제보와 사진을 최신순으로 확인하고, 현재 위치·위험 유형·익명 여부와
사진 최대 3장을 multipart로 전송할 수 있다.

```
Android ──(multipart, Bearer JWT)──▶ Spring Boot ──(S3 호환 API)──▶ Supabase Storage (비공개 버킷)
                                         └─▶ risk_report / risk_report_image 저장
조회: 앱 → 백엔드 → 만료 시간이 있는 사진 URL(기본 10분) 발급 → 앱이 그 URL로 이미지 표시
```

### API

사진을 함께 보내므로 작성·사진 추가는 JSON이 아니라 **`multipart/form-data`**로 요청한다.

| 메서드 | 경로 | 토큰 | 설명 |
|---|---|---|---|
| POST | `/api/risk-reports` | 필요 | 제보 작성 (사진 0~3장 함께) |
| GET | `/api/risk-reports?page=0&size=20` | 필요 | 반려된 글을 제외한 전체 제보 목록 (최신순, size 최대 50) |
| GET | `/api/risk-reports/{reportId}` | 필요 | 제보 상세 + 사진 URL. 반려 글은 작성자만 조회 가능 |
| POST | `/api/risk-reports/{reportId}/images` | 필요 | 내 제보에 사진 추가 |
| DELETE | `/api/risk-reports/{reportId}/images/{imageId}` | 필요 | 내 제보의 사진 1장 삭제 |

제보 작성 요청 필드:

| 필드 | 필수 | 설명 |
|---|---|---|
| `riskType` | O | `DARK_ROAD`(어두운 길) / `DAMAGED_SIDEWALK`(파손된 보도) / `CONSTRUCTION`(공사 구간) / `ILLEGAL_PARKING`(불법 주정차) / `OTHER`(기타). 한글 값도 허용 |
| `lat`, `lng` | O | 위도(-90~90), 경도(-180~180) |
| `address` | - | 255자 이하 |
| `description` | - | 500자 이하 |
| `anonymous` | - | 익명 제보 여부, 기본 `false` |
| `images` | - | 사진 파일. 같은 이름(`images`)으로 여러 개 |

제보 응답 예시:

```json
{ "reportId": 12, "riskType": "DARK_ROAD", "riskTypeLabel": "어두운 길",
  "lat": 36.809, "lng": 127.147, "address": "천안역 동부광장 인근", "description": "가로등이 꺼져 있음",
  "anonymous": false, "authorName": "김안전", "mine": false,
  "status": "PENDING", "createdAt": "...", "updatedAt": null,
  "images": [ { "imageId": 31, "url": "https://...", "urlExpiresInSeconds": 600, "createdAt": "..." } ] }
```

> 사진 `url`은 일정 시간 뒤 만료된다. 앱에 저장해 두지 말고, 필요할 때 제보를 다시 조회해서 새 URL을 받는다.

### 규칙

| 구분 | 규칙 |
|---|---|
| 사진 형식 | JPEG / PNG / WebP만 허용. 파일명이나 Content-Type이 아니라 **파일 내용**으로 판별 |
| 사진 크기·개수 | 1장 10MB 이하, 제보 1건당 최대 3장 |
| 조회 범위 | `PENDING`, `APPROVED` 제보는 로그인 사용자에게 공개. `REJECTED` 제보는 작성자만 상세 조회 가능 |
| 익명 | 익명 제보의 `authorName`은 항상 `익명`으로 응답 |
| 수정 권한 | 사진 추가·삭제는 본인 제보만 가능. 다른 사람의 제보는 404 |
| 사진 변경 | 처리 전(`PENDING`) 제보만 사진 추가·삭제 가능 |
| 실패 처리 | 사진 업로드 후 DB 저장이 실패하면 올라간 파일을 자동 삭제. 사진 삭제 시 DB 반영 후 저장소 파일 삭제 |

새로 추가된 상태 코드:

| 코드 | 의미 |
|---|---|
| 400 | 지원하지 않는 사진 형식, 사진 개수 초과, 필수 값(`lat` 등)·파일 누락, 잘못된 위험 유형 |
| 404 | 제보·사진 없음 (다른 사람의 제보 포함) |
| 413 | 사진 1장이 10MB 초과 |
| 415 | multipart가 아닌 형식으로 요청 |
| 502 | 이미지 저장소(Supabase Storage) 통신 실패 |

> 10MB를 넘는 파일은 서버가 413 응답 대신 업로드 도중 연결을 끊을 수 있다.
> **Android에서는 업로드 전에 사진을 압축하고 크기를 확인**하는 것을 권장한다 (긴 변 1600px, JPEG 품질 80 정도면 1장에 수백 KB).

### 저장소 설정 (`STORAGE_TYPE`)

| 값 | 동작 |
|---|---|
| `supabase` | Supabase Storage에 저장. `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET` 필요 |
| `local` 또는 생략 | **개발용.** `backend/local-storage/` 폴더에 저장하고 `/local-storage/**` 주소로 제공 (만료 없음, 인증 없음). 저장소 키가 없는 팀원도 백엔드를 실행할 수 있다 |

- 값은 Supabase Dashboard → Storage → Settings(S3 Configuration)에서 확인·발급한다.
- **S3 Access Key는 모든 버킷에 접근할 수 있는 서버 전용 키다.** Android 코드, 채팅방, 저장소에 올리지 않는다.
- 서버 시작 로그에 `이미지 저장소: Supabase Storage` 또는 `이미지 저장소: 로컬 폴더`가 표시된다.

### 추가/수정된 파일 — backend/

| 파일 | 설명 |
|---|---|
| `storage/` (신규) | `ImageStorage` 인터페이스, `SupabaseImageStorage`(AWS SDK v2 S3 클라이언트), `LocalImageStorage` + `LocalStorageWebConfig`(개발용), `ImageStorageException` |
| `report/` (신규) | `RiskReport`, `RiskReportImage` 엔티티, `RiskType`, `ReportStatus`, Repository, `RiskReportService`, `RiskReportImageService`, `RiskReportController`, 응답 DTO, `RiskReportNotFoundException` |
| `global/GlobalExceptionHandler.java` | 400(파라미터·파일 누락), 404(제보), 413, 415, 502 처리 추가 |
| `build.gradle.kts` | AWS SDK for Java v2 (`software.amazon.awssdk:s3`) 추가 |
| `src/main/resources/application.yml` | 업로드 크기 제한(1장 10MB, 요청 35MB), `storage.*` 설정 추가 |
| `.env.example` | `STORAGE_TYPE`, `STORAGE_ENDPOINT` 등 저장소 환경변수 추가 |
| `.gitignore` | `local-storage/` 제외 |
| `http/risk-report-image-test.ps1` | PowerShell 자동 테스트 (21개 케이스) |

### 테스트 방법

백엔드를 실행한 상태에서 `backend` 폴더 기준으로:

```powershell
powershell -ExecutionPolicy Bypass -File .\http\risk-report-image-test.ps1
```

제보 작성·조회, 사진 추가·삭제, 형식·크기·개수 제한, 다른 사용자의 사진 변경 차단을 확인하고,
사진 URL로 직접 내려받아 **저장소에 실제로 저장·삭제됐는지**까지 검사한다 (2026-10-16 Supabase 기준 21개 모두 통과).
실행할 때마다 테스트 계정 2개, 제보 2건, 사진 2장이 DB와 버킷에 남는다.

### 보류된 기능

- 관리자 승인/반려(`APPROVED` 시 `risk_area` 연결)
- 제보 삭제 (구현 시 저장소 파일도 함께 삭제해야 함 — DB 행은 CASCADE로 지워지지만 파일은 남음)

## 보호자 엔티티 DB v3 반영 · 보호자 수정 API — 2026-10-20 (backend)

`Guardian` 엔티티가 DB v3 `guardian` 테이블의 새 컬럼을 매핑하지 않던 문제를 고치고,
보호자/지인 연결 기능(초대·수락)의 바탕이 되는 **대표 보호자**와 **보호자 정보 수정 API**를 추가했다.
초대·수락·연결 해제 API는 아직 없다.

### Android 쪽 영향

- 보호자 응답에 필드가 추가됐다. 기존 필드는 그대로라 지금 코드는 수정 없이 동작한다.
- 보호자 추가 요청에 `isPrimary`, `canViewLocation`을 선택적으로 보낼 수 있다 (생략 가능).

```json
{ "guardianId": 3, "name": "김엄마", "phone": "010-9876-5432",
  "relationship": "PARENT", "relationshipLabel": "부모",
  "isPrimary": true, "registrationStatus": "UNREGISTERED", "canViewLocation": false,
  "createdAt": "...", "connectedAt": null }
```

### API

| 메서드 | 경로 | 토큰 | 설명 |
|---|---|---|---|
| PATCH | `/api/guardians/{guardianId}` | 필요 | 보호자 정보 수정 (신규). 보낸 필드만 바뀐다 |

```json
PATCH /api/guardians/3
{ "name": "엄마", "relationship": "PARENT", "isPrimary": true, "canViewLocation": true }
```

### 규칙

| 구분 | 규칙 |
|---|---|
| 대표 보호자 | 사용자당 최대 1명 (DB `uq_guardian_primary_active`). 대표가 없을 때 추가한 보호자는 자동으로 대표가 된다. `isPrimary: true`로 추가·수정하면 기존 대표는 자동 해제 |
| 대표 해제 | 대표에게 `isPrimary: false`를 보내면 400. 다른 보호자를 대표로 지정하면 바뀐다 |
| 대표 삭제 | 남은 보호자 중 가장 먼저 등록된 사람이 대표가 된다 |
| 회원가입 | `guardians` 중 `isPrimary: true`는 1명까지 (2명 이상이면 400) |
| 수정 | `name`, `relationship`, `isPrimary`, `canViewLocation`만 수정 가능. `phone`을 보내면 400 (보호자 계정 연결의 기준 값이라 삭제 후 다시 등록) |
| 연결 상태 | 새 보호자는 `UNREGISTERED`. 삭제 시 `INVITED`·`CONNECTED`였다면 `REVOKED`로 바뀐다 |
| 위치 열람 | `canViewLocation` 기본값 false |

### 추가/수정된 파일 — backend/

| 파일 | 설명 |
|---|---|
| `guardian/Guardian.java` | `guardian_user_id`, `is_primary`, `registration_status`, `can_view_location`, `connected_at` 매핑. setter 대신 `markPrimary()`, `changeName()` 등 의미 있는 메서드로 변경 |
| `guardian/RegistrationStatus.java` (신규) | `UNREGISTERED` / `INVITED` / `CONNECTED` / `REVOKED` |
| `guardian/GuardianUpdateRequest.java` (신규) | 수정 요청 |
| `guardian/GuardianRequest.java`, `GuardianResponse.java` | 새 필드 추가 |
| `guardian/GuardianService.java`, `GuardianController.java`, `GuardianRepository.java` | 대표 보호자 규칙, 수정 API, 삭제 시 상태 변경 |
| `db/v3/06_backfill_primary_guardian.sql` (신규) | 기존 사용자에게 대표 보호자 지정 (1회 실행) |
| `http/auth-guardian-test.ps1`, `auth-guardian.http` | 21~30번 케이스 추가 (총 34개) |

### DB 작업 (Supabase에 한 번 실행)

v3 마이그레이션 때 `is_primary`가 기본값 false로 추가돼서, 기존 사용자는 대표 보호자가 없다.
Supabase SQL Editor에서 `backend/db/v3/06_backfill_primary_guardian.sql`을 한 번 실행한다
(사용자별로 가장 먼저 등록된 보호자를 대표로 지정, 여러 번 실행해도 결과 같음). 스키마는 바뀌지 않는다.

### 보류된 기능

- 연결 초대 보내기·받은 초대 조회·수락/거절·연결 해제 API
- 보호자 입장의 "내가 보호 중인 사용자" 목록, FCM 토큰 등록, 알림 목록

## 보호자 계정 연결 API (초대 · 수락 · 해제) — 2026-10-20 (backend)

보호자로 등록된 사람이 **같은 전화번호로 앱에 가입**하면, 사용자가 보낸 연결 초대를 받아 수락할 수 있다.
연결되면 보호자 쪽에서 "내가 보호 중인 사용자" 목록을 볼 수 있다. 위치 조회·알림 발송은 아직 없다.

### 흐름

```
사용자(보호받는 사람)                       보호자 계정 (같은 번호로 가입)
POST /api/guardians/{id}/invite  ──────▶  GET  /api/guardians/invitations
   UNREGISTERED → INVITED                 POST /api/guardians/invitations/{id}/accept  → CONNECTED
                                          POST /api/guardians/invitations/{id}/reject  → UNREGISTERED
POST /api/guardians/{id}/revoke                GET  /api/guardians/wards
   INVITED → UNREGISTERED (초대 취소)           POST /api/guardians/wards/{id}/leave         → REVOKED
   CONNECTED → REVOKED (연결 해제)
```

`{id}`는 모두 보호자 행의 `guardianId`다 (초대 ID = 연결 ID).

### API

| 메서드 | 경로 | 호출하는 사람 | 설명 |
|---|---|---|---|
| POST | `/api/guardians/{guardianId}/invite` | 사용자 | 연결 초대 보내기. UNREGISTERED·REVOKED에서 가능 |
| POST | `/api/guardians/{guardianId}/revoke` | 사용자 | 초대 취소(INVITED → UNREGISTERED) 또는 연결 해제(CONNECTED → REVOKED) |
| GET | `/api/guardians/invitations` | 보호자 | 내 전화번호로 온 초대 목록 |
| POST | `/api/guardians/invitations/{guardianId}/accept` | 보호자 | 수락 → CONNECTED |
| POST | `/api/guardians/invitations/{guardianId}/reject` | 보호자 | 거절 → UNREGISTERED |
| GET | `/api/guardians/wards` | 보호자 | 내가 보호 중인 사용자 목록 |
| POST | `/api/guardians/wards/{guardianId}/leave` | 보호자 | 스스로 연결 끊기 → REVOKED |

받은 초대 응답 (수락 전에는 상대 이름과 관계만 보여준다):

```json
{ "guardianId": 7, "wardName": "김안심", "relationship": "PARENT", "relationshipLabel": "부모" }
```

보호 대상 응답:

```json
{ "guardianId": 7, "wardName": "김안심", "wardPhone": "010-1234-5678",
  "relationship": "PARENT", "relationshipLabel": "부모",
  "isPrimary": true, "canViewLocation": true, "connectedAt": "..." }
```

### 규칙

| 구분 | 규칙 |
|---|---|
| 초대 매칭 | 보호자 행의 `phone`과 같은 번호로 가입한 계정이 받는다. 초대할 때 그 번호의 계정이 없어도 되고, 나중에 가입하면 보인다 |
| 상태 오류 | 이미 초대·연결된 보호자 재초대, 이미 수락한 초대 재수락, 연결되지 않은 보호자 해제 등은 **409** |
| 접근 제한 | 내 번호가 아닌 초대, 내가 연결되지 않은 사용자, 남의 보호자는 **404** (존재 여부를 드러내지 않음) |
| 위치 열람 | `canViewLocation`은 사용자만 바꿀 수 있다 (`PATCH /api/guardians/{id}`). 보호자는 조회만 |
| 삭제와의 관계 | 사용자가 보호자를 삭제(DELETE)하면 연결도 REVOKED가 된다. 해제(revoke)는 보호자 행을 남긴다 |
| 동시 요청 | 수락·거절·끊기는 보호자 행을 잠그고 처리한다 |

### ⚠ 보안 주의 (MVP 한계)

가입 시 **전화번호 본인 인증이 없다**. 다른 사람의 번호로 가입하면 그 사람에게 온 초대를 수락할 수 있고,
사용자가 위치 열람을 허용해 두었다면 위치 조회 API가 생긴 뒤 위치까지 볼 수 있게 된다.
본인 인증(`users.identity_verified`)을 도입하면 `GuardianConnectionService.accept()`에서 인증된 계정만 수락하도록 바꿔야 한다.

### 추가/수정된 파일 — backend/

| 파일 | 설명 |
|---|---|
| `guardian/GuardianConnectionController.java`, `GuardianConnectionService.java` (신규) | 연결 API 7개 |
| `guardian/GuardianInvitationResponse.java`, `WardResponse.java` (신규) | 받은 초대, 보호 대상 응답 |
| `guardian/GuardianStateException.java` (신규) | 상태가 맞지 않을 때 409 |
| `guardian/Guardian.java` | 상태 전이 메서드 `invite()`, `cancelInvite()`, `accept()`, `reject()`, `revoke()` |
| `guardian/GuardianRepository.java` | 번호로 초대 조회, 보호 대상 조회, 잠금 조회 |
| `global/GlobalExceptionHandler.java` | `GuardianStateException` → 409 |
| `http/auth-guardian-test.ps1`, `auth-guardian.http` | 31~52번 케이스 추가 (총 56개) |

DB 변경은 없다 (v3 `guardian` 컬럼을 그대로 사용).

### 보류된 기능

- ~~초대 푸시 알림~~ → 2026-10-29 구현 (아래 "알림함 API · 보호자 연결 알림")
- 보호자의 위치 조회 (`canViewLocation` 확인 후 응답)
- 전화번호 본인 인증

## 푸시 알림(FCM) 기반 · 기기 토큰 API — 2026-10-27 (backend)

SOS 보호자 알림의 바탕 작업. 앱이 FCM 토큰을 서버에 등록하고, 서버가 그 기기로 푸시를 보낼 수 있다.
SOS API 자체는 아직 없다 (명세: Claude 프로젝트 문서 `emergency-sos-api-v1`).

### API (모두 JWT 필요)

| 메서드 | 경로 | 언제 | 요청 본문 |
|---|---|---|---|
| PUT | `/api/users/me/devices` | 로그인 직후, `onNewToken`(토큰 갱신) 때 | `{ "fcmToken": "...", "deviceType": "ANDROID" }` (deviceType 선택: ANDROID / WEAR_OS) |
| DELETE | `/api/users/me/devices` | 로그아웃 때 | `{ "fcmToken": "..." }` |
| POST | `/api/dev/push-test` | (개발용) 내 기기로 테스트 푸시 | `{ "title": "...", "body": "..." }` 둘 다 선택 |

### 규칙
- 같은 토큰을 다시 등록하면 새 행을 만들지 않고 기존 기기를 다시 활성화한다.
- 같은 폰에서 다른 계정으로 로그인해 등록하면 기기의 주인이 새 계정으로 바뀐다. 이전 계정 알림은 이 폰으로 더 이상 오지 않는다.
- 해제(DELETE)는 내 기기가 아니거나 없는 토큰이어도 200이다. 로그아웃이 이 API 때문에 실패하지 않게 하려는 것이다.
- FCM이 "없는/잘못된 토큰"(UNREGISTERED, INVALID_ARGUMENT, SENDER_ID_MISMATCH)이라고 답하면 그 기기를 자동으로 비활성화한다.
- 푸시는 Android 우선순위 HIGH로 보낸다 (절전 상태에서도 바로 도착하게. SOS용).
- 400: fcmToken 없음·공백·512자 초과, 없는 deviceType / 401: JWT 없음

### 설정 (`backend/.env`)
| 키 | 값 | 설명 |
|---|---|---|
| `FIREBASE_CREDENTIALS_PATH` | `firebase-service-account.json` | Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → 새 비공개 키. 상대 경로는 backend 폴더 기준. **생략하면 푸시를 보내지 않고 서버 로그에만 출력**(키 없는 팀원용) |
| `DEV_PUSH_TEST_ENABLED` | `true` | `/api/dev/push-test` 켜기. 운영 배포에서는 넣지 않는다 (없으면 404) |

- 서비스 계정 키는 Firebase 프로젝트 관리자 권한이다. `backend/firebase-service-account.json`, `*firebase-adminsdk*.json`은 `.gitignore`에 등록되어 있다. 메신저로 공유하지 말고, 필요한 팀원은 콘솔에서 따로 발급한다.
- 경로를 넣었는데 파일이 없거나 잘못된 파일이면 서버가 시작되지 않는다 (조용히 콘솔 모드로 바뀌지 않게).
- 문자(SMS)는 MVP 동안 항상 서버 로그 출력이다 (`ConsoleSmsSender`).

### 추가/수정된 파일 — backend/
- `build.gradle.kts`: `com.google.firebase:firebase-admin:9.8.0`
- `application.yml`: `firebase.credentials-path`, `dev.push-test-enabled`
- `.env.example`, `.gitignore`
- `device/`: `UserDevice`(user_device 엔티티), `DeviceType`, `UserDeviceRepository`, `DeviceService`, `DeviceController`, `DeviceRequests`, `DeviceResponse`
- `notification/`: `PushSender`·`FcmPushSender`·`ConsolePushSender`, `SmsSender`·`ConsoleSmsSender`, `PushSenderConfig`, `PushMessage`, `PushResult`, `DevPushTestController`, `NotificationService`(pushToUser·sendSms 추가)
- `http/device-push-test.ps1`, `http/device-push.http`

### 테스트 방법
```
powershell -ExecutionPolicy Bypass -File .\http\device-push-test.ps1
```
가짜 토큰을 쓰므로 폰에는 알림이 가지 않는다. FCM 모드에서는 22번이 "Firebase 서버 인증 정상"을 확인한다
(가짜 토큰만 INVALID_ARGUMENT로 거절되면 서비스 계정 키는 정상). 실제 도착 확인은 앱이 토큰을 등록한 뒤 `/api/dev/push-test`.

### 프론트(Android) 반영 완료
- Firebase 콘솔에서 Android 앱(패키지 `com.safewalk`) 등록 → `google-services.json`을 `android/mobile/`에
- 루트 `build.gradle.kts`에 `com.google.gms.google-services` 플러그인, `mobile`에 플러그인 + `firebase-messaging`
- `FirebaseMessagingService`: `onNewToken`에서 PUT 호출, `onMessageReceived`에서 data(`type` 등) 처리
- 로그인 직후 PUT, 로그아웃 시 JSON 본문을 포함한 DELETE 호출
- Android 13 이상 `POST_NOTIFICATIONS` 권한 요청
- 워치는 따로 등록하지 않아도 폰 알림이 Wear OS로 전달된다

## 알림함 API · 보호자 연결 알림 — 2026-10-29 (backend)

푸시는 한 번 오고 사라지므로, 모든 알림을 `notification` 테이블에 기록하고 앱 알림함에서 다시 볼 수 있게 했다.
보호자 연결 상태가 바뀌면 상대방에게 알림(기록 + 푸시)이 간다. DB 변경 없음.

### API (모두 JWT 필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/notifications?page=0&size=20` | 내 알림 최신순. 응답 `{ items, page, size, hasNext, unreadCount }`. size 1~50 |
| GET | `/api/notifications/unread-count` | 안 읽은 알림 수 `{ unreadCount }` (배지용) |
| PATCH | `/api/notifications/{id}/read` | 읽음 처리. 이미 읽었으면 처음 읽은 시각 유지. 다른 사람 알림이면 404 |
| PATCH | `/api/notifications/read-all` | 전체 읽음 `{ updatedCount }` |

알림 한 건: `{ notificationId, type, title, content, referenceType, referenceId, read, readAt, createdAt }`
- `type`: GUARDIAN / EMERGENCY / RISK_AREA (알림함 분류)
- `referenceType` + `referenceId`: 눌렀을 때 이동할 대상 (보호자 알림은 GUARDIAN + guardianId)

### 보호자 연결 알림

| 이벤트 (푸시 data.type) | 받는 사람 | 언제 |
|---|---|---|
| `GUARDIAN_INVITED` | 그 전화번호로 가입한 보호자 계정 | 초대를 보냄 (가입한 계정이 없으면 보내지 않음) |
| `GUARDIAN_ACCEPTED` | 사용자 | 보호자가 수락 |
| `GUARDIAN_DISCONNECTED` | 보호자 계정 | 사용자가 연결 해제(revoke)하거나 연결된 보호자를 삭제 |
| `GUARDIAN_LEFT` | 사용자 | 보호자가 스스로 연결 끊기(leave) |

- 초대 거절, 보낸 초대 취소는 알리지 않는다 (상대에게 부담을 주지 않기 위해. 상태는 목록에서 확인 가능).
- 푸시 data: `type`(위 이벤트), `category`(GUARDIAN), `notificationId`, `referenceType`, `referenceId`, `guardianId`.

### 동작 원칙 (`NotificationService.notify`)
- 알림 기록은 본 작업(초대·수락 등)과 같은 트랜잭션 → 본 작업이 실패하면 기록도 취소.
- 푸시는 그 트랜잭션이 **커밋된 뒤** 발송 → 실패한 작업의 알림이 먼저 가는 일 없음.
- 푸시가 실패해도 본 작업은 성공 (기록은 남으므로 알림함에서 볼 수 있음).
- SOS 등 다른 기능도 이 메서드 하나로 알림을 보낸다.
- 지금은 커밋 직후 같은 요청 안에서 푸시를 보내므로, 초대·수락 응답이 FCM 응답 시간만큼(보통 1초 이내) 늦어질 수 있다. 필요하면 비동기로 바꾼다.

### 추가/수정된 파일 — backend/
- `notification/`: `Notification`(엔티티), `NotificationType`, `ReferenceType`, `NotificationRepository`, `NotificationController`, `NotificationResponses`, `NotificationNotFoundException`, `PushDelivery`(푸시 + 토큰 정리, 새 트랜잭션), `NotificationService`(notify·알림함 추가)
- `guardian/`: `GuardianNotifier`(신규), `GuardianConnectionService`·`GuardianService`(알림 호출 추가)
- `user/UserRepository`: `findByPhoneAndDeletedAtIsNull` (초대 받을 계정 찾기)
- `global/GlobalExceptionHandler`: 알림 404
- 테스트: `http/notification-test.ps1`

### 테스트 방법
```
powershell -ExecutionPolicy Bypass -File .\http\notification-test.ps1
```
아빠·아이 계정으로 초대 → 거절 → 재초대 → 취소 → 수락 → 연결 끊기 → 해제 → 삭제를 진행하며 알림함 기록을 확인한다 (35개).
기존 `auth-guardian-test.ps1`, `device-push-test.ps1`도 함께 다시 실행해 영향이 없는지 확인한다.

### 프론트(Android) 반영 완료
- 알림함 화면: 목록(무한 스크롤은 `hasNext`), 항목 누르면 `PATCH .../read` 후 `referenceType`에 따라 이동 (GUARDIAN → 초대 목록 또는 보호자 목록)
- 탭·아이콘 배지: `unread-count`
- 푸시 수신(`onMessageReceived`) 시 `data.type`으로 화면 분기, 알림함 새로고침

## 다운받아 실행할 때 확인/수정할 것

- **android** — `local.properties`의 `BACKEND_BASE_URL`을 실행할 PC의 로컬 네트워크 IP로
  설정해야 한다 (`ipconfig`로 확인). 에뮬레이터 표준 별칭 `10.0.2.2`와 `adb reverse`는 이 환경에서
  동작하지 않아 실제 IP 방식을 쓰고 있다. `local.properties.example`을 `local.properties`로
  복사하고 Kakao API 키도 입력해야 한다.
- **backend** — `.env.example`을 참고해 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`(Supabase),
  `JWT_SECRET` 환경변수를 실행 전에 설정해야 한다 (`backend/.env` 파일 — `./gradlew bootRun` 실행 시
  자동으로 읽힘. IntelliJ Run 버튼으로 실행할 때는 Run Configuration에 직접 입력). 이 값들을
  `application.yml`에 직접 쓰거나 커밋하지 않는다.
- **backend (제보 사진)** — Supabase Storage를 쓰려면 `.env`에 `STORAGE_TYPE=supabase`와
  `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET`을 넣는다.
  생략하면 개발용 로컬 폴더(`backend/local-storage/`)에 저장된다.
- **backend (푸시)** — 실제 FCM 발송은 `.env`에 `FIREBASE_CREDENTIALS_PATH`(서비스 계정 키 경로)가 필요하다.
  생략하면 서버 로그에만 출력된다. 키 파일은 커밋하지 않는다.
