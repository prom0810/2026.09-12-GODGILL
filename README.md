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