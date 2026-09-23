# 2026.09-12-GODGILL

## 프로젝트 구조

본 저장소는 모바일(안드로이드) 앱, 웨어러블(Wear OS) 앱, 백엔드 서버를 하나의 모노레포로 관리합니다.

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
    └── src/main/java/com/safewalk/
        ├── global/                  # 보안 필터, 예외처리, DB 설정 등 애플리케이션 전역 인프라 코드
        ├── auth/                    # 회원가입/로그인 API
        ├── user/                    # 사용자 유형, 보호자 연락처 관리
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
| `src/main/java/com/safewalk/auth/` (`AuthController.kt` 등) | 회원가입/로그인 API(`/api/auth/signup`, `/api/auth/login`) 구현 |
| `src/main/resources/application.yml` | `JWT_SECRET` 등 인증 관련 환경변수 참조 추가 |
| `.env.example` | 필요한 환경변수 예시 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`) |

## 다운받아 실행할 때 확인/수정할 것

- **android** — `common/network/ApiClient.kt`의 `baseUrl`을 실행할 PC의 로컬 네트워크 IP로
  바꿔야 한다 (`ipconfig`로 확인). 에뮬레이터 표준 별칭 `10.0.2.2`와 `adb reverse`는 이 환경에서
  동작하지 않아 실제 IP 방식을 쓰고 있다. `local.properties.example`을 `local.properties`로
  복사하고 Kakao API 키도 입력해야 한다.
- **backend** — `.env.example`을 참고해 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`(Supabase),
  `JWT_SECRET` 환경변수를 실행 전에 설정해야 한다 (IntelliJ Run Configuration 또는 터미널
  `export`). 이 값들을 `application.yml`에 직접 쓰거나 커밋하지 않는다.
