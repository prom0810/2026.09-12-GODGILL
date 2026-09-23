# SafeWalk Backend


##안드로이드 추가 사항


## 인증(로그인/회원가입) 및 백엔드 연동 — 2026-09-23 추가

회원가입/로그인 화면이 실제 백엔드 API(`/api/auth/signup`, `/api/auth/login`)를 호출하도록
연결했다. 자세한 파일 목록과 설계 결정은
[`docs/auth-handoff-2026-09-23.md`](../docs/auth-handoff-2026-09-23.md) 참고.

- **백엔드 주소 설정 (반드시 확인):** `common/network/ApiClient.kt`의 `baseUrl`이 현재
  개발 PC의 로컬 네트워크 IP(예: `http://192.168.56.1:8080`)로 하드코딩되어 있다. 이 값은
  PC/네트워크마다 다르고 DHCP로 바뀔 수 있으므로, 다른 PC에서 실행하거나 갑자기 연결이 안
  되면 `ipconfig`(Windows)로 현재 IP를 확인해서 이 값을 직접 바꿔야 한다. 에뮬레이터 표준
  별칭인 `10.0.2.2`와 `adb reverse` 터널 방식은 이 프로젝트 환경에서 원인 불명의 이유로
  동작하지 않아 실제 IP 방식으로 우회했다 (자세한 경위는 handoff 문서 참고).
- 백엔드는 HTTPS가 아니므로 `AndroidManifest.xml`에 `android:usesCleartextTraffic="true"`가
  설정되어 있다. 배포 전 백엔드를 HTTPS로 바꾸고 이 속성은 제거해야 한다.
- 통신은 이 프로젝트의 기존 방식(Retrofit 없이 `HttpURLConnection` 직접 사용)을 그대로
  따랐다 — `KakaoPlaceSearch`/`NearbyFacilitySearch`와 같은 패턴.
- 회원가입 화면에는 아직 이름·전화번호 입력이 없다(이메일·비밀번호·사용자 유형만). 사용자
  유형(성인/미성년자) 구분은 현재 뼈대만 있고 유형별 추천 로직과는 아직 연결되지 않았다.
- 로그인 성공 시 발급되는 JWT는 `TokenStore`(SharedPreferences)에 저장만 하고, 아직 이후
  API 호출에 자동으로 실어 보내지는 않는다(그런 API가 아직 없음).

## 추가 파일

- `.gitignore`(저장소 루트): 로컬 키와 생성물 제외
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`: 프로젝트 및 SDK 저장소 설정
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: Gradle 실행 환경
- `local.properties.example`: 개인 설정 템플릿
- `mobile/build.gradle.kts`, `mobile/proguard-rules.pro`: 앱, 의존성, 키 주입 및 난독화 규칙
- `mobile/src/main/AndroidManifest.xml`: 앱 초기화 클래스, 시작 화면, 인터넷 권한, OpenGL 요구사항
- `mobile/src/main/java/com/safewalk/common/SafeWalkApplication.kt`: SDK 초기화
- `mobile/src/main/java/com/safewalk/common/network/ApiClient.kt`: 백엔드 HTTP 클라이언트, `baseUrl` 설정
- `mobile/src/main/java/com/safewalk/auth/`: 인증 API 모델, 호출, 토큰 저장 (`AuthModels.kt`, `AuthApi.kt`, `AuthRepository.kt`, `TokenStore.kt`)
- `mobile/src/main/java/com/safewalk/login/`: 로그인 화면 (API 연동됨)
- `mobile/src/main/java/com/safewalk/signup/`: 회원가입 화면 (신규)
- `mobile/src/main/java/com/safewalk/map/MapActivity.kt`: 지도와 생명주기 처리
- `mobile/src/main/res/layout/activity_map.xml`, `res/values/strings.xml`, `res/values/themes.xml`: 화면 리소스



--------------------------------------------------------------------------------------------------------------------------------





Spring Boot(Kotlin) 백엔드. DB는 로컬이 아니라 **Supabase PostgreSQL**(공용 서버, pgAdmin으로 관리)을 사용한다.
테이블 10개는 이미 pgAdmin에서 생성되어 있으므로, 이 프로젝트의 JPA는 스키마를 만들지 않고
**검증만** 한다 (`spring.jpa.hibernate.ddl-auto=validate`).

## 1. 실행 전 준비

IntelliJ에서 이 `backend/` 폴더를 프로젝트로 연다 (모노레포의 `android/`와는 별개의 Gradle 프로젝트).

다음 3개 환경변수가 필요하다. 값은 Supabase Dashboard → 프로젝트 → **Connect** →
**Session pooler** → **View parameters** 에서 확인한다.

| 환경변수 | 설명 | 예시 |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://HOST:5432/postgres` |
| `DB_USERNAME` | Session Pooler 사용자명 | `postgres.[PROJECT_REF]` |
| `DB_PASSWORD` | Supabase DB 비밀번호 | - |

값이 어떤 형태인지는 `.env.example`을 참고한다. **이 값을 `application.yml`에 직접 써넣거나
GitHub에 커밋하지 않는다.** `application.yml`은 항상 `${DB_URL}`처럼 환경변수 참조만 남겨두고,
실제 값은 아래 방법으로 실행 시점에만 주입한다.

로컬에서 실행할 때 환경변수를 주입하는 방법 두 가지:

**(A) IntelliJ Run/Debug Configuration**
`Run` → `Edit Configurations` → `SafewalkApplication` → `Environment variables`에
`DB_URL=...;DB_USERNAME=...;DB_PASSWORD=...` 형태로 입력.

**(B) 터미널에서 직접 실행할 때**
```bash
export DB_URL="jdbc:postgresql://HOST:5432/postgres"
export DB_USERNAME="postgres.PROJECT_REF"
export DB_PASSWORD="실제_비밀번호"
./gradlew bootRun
```

## 2. 빌드/실행

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootRun     # 서버 실행 (환경변수 필요)
```

## 3. 소스 구조

Kotlin이지만 소스 폴더는 `src/main/kotlin`이 아니라 **`src/main/java`를 그대로 사용**한다.
Kotlin Gradle 플러그인은 `src/main/kotlin`과 `src/main/java`를 둘 다 기본 Kotlin 소스
디렉터리로 인식하므로, 저장소의 기존 경로를 유지하면서 `.kt` 파일을 둘 수 있다.
`src/main/kotlin` 폴더는 만들지 않는다.

`com.safewalk` 아래 도메인별로 분리되어 있다.

```
com.safewalk
├── global          # 보안 필터, 예외처리, 공통 설정
├── auth            # 회원가입/로그인 API
├── user            # 사용자 유형, 보호자 연락처
├── route           # 안전 경로 추천 알고리즘
├── notification    # 알림 발송
├── emergency       # SOS, 위치 공유
└── datalog         # 웨어러블 데이터 수신
```

## 4. Entity 추가 시 참고

`build.gradle.kts`에 `kotlin("plugin.jpa")`를 이미 넣어뒀기 때문에, `@Entity` 클래스에
`open`을 일일이 붙이지 않아도 Hibernate 프록시 생성이 된다. Entity/Repository를 추가할 때
바로 쓰면 된다.

## 5. 인증 API (회원가입/로그인) — 2026-09-23 추가

`/api/auth/signup`, `/api/auth/login` 두 엔드포인트를 구현했다. 자세한 파일 목록과 설계
결정은 [`docs/auth-handoff-2026-09-23.md`](../docs/auth-handoff-2026-09-23.md) 참고.

- 요청: `{ "email": "...", "password": "...", "userType": "ADULT" | "MINOR" }` (JSON)
- 응답: `{ "success": boolean, "data": {...} | null, "message": "..." }` (기존 `ApiResponse`/
  `GlobalExceptionHandler` 공통 포맷 그대로 사용)
- 비밀번호는 `spring-security-crypto`의 BCrypt로 해시해 저장한다. 로그인 성공 시 JWT를
  발급하지만, **아직 이 토큰으로 다른 API를 보호하는 필터는 없다** (발급까지만 구현됨).
- 검증은 의도적으로 최소한만 한다: 이메일/비밀번호 공백 여부, 이메일 중복(가입 시)/
  일치(로그인 시)만 확인한다. 이메일 형식, 비밀번호 길이 등 세부 규칙은 없다.
- `users` 테이블의 `name`/`phone`은 NOT NULL이라 회원가입 화면에 아직 입력란이 없는 동안은
  임시값(`name`은 이메일 앞부분, `phone`은 `000-0000-0000`)을 채워 넣는다 — 실제 입력 화면을
  추가하면 `AuthService.kt`의 이 부분을 먼저 교체해야 한다.
- 사용자 유형(`ADULT`/`MINOR`) 구분은 저장만 하고 있고, 유형별 가중치/추천 로직에는 아직
  연결되어 있지 않다 (요청대로 뼈대만 구현).
- 새 환경변수 `JWT_SECRET` 필요 (`.env.example` 참고, 생략 시 `application.yml`의 로컬
  기본값 사용 — 운영 배포 전 교체 필수).
