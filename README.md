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
Android 쪽은 아직 반영되지 않았다 (아래 "프론트 반영 필요" 참고).

### ⚠️ 프론트 반영 필요 (반영 전까지 앱 회원가입이 실패함)

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

## 다운받아 실행할 때 확인/수정할 것

- **android** — `common/network/ApiClient.kt`의 `baseUrl`을 실행할 PC의 로컬 네트워크 IP로
  바꿔야 한다 (`ipconfig`로 확인). 에뮬레이터 표준 별칭 `10.0.2.2`와 `adb reverse`는 이 환경에서
  동작하지 않아 실제 IP 방식을 쓰고 있다. `local.properties.example`을 `local.properties`로
  복사하고 Kakao API 키도 입력해야 한다.
- **backend** — `.env.example`을 참고해 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`(Supabase),
  `JWT_SECRET` 환경변수를 실행 전에 설정해야 한다 (IntelliJ Run Configuration 또는 터미널
  `export`). 이 값들을 `application.yml`에 직접 쓰거나 커밋하지 않는다.
