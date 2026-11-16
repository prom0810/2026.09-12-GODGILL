# GODGILL DB 스키마 v3 (2026-10-15, 2026-10-26·2026-11-11 갱신)

## 공통 시설 정책으로 전환

현재 정책은 두 연령대에 CCTV 0.02·가로등 0.04·경찰시설 0.04·나머지 0을 적용하고 전체 보너스를 0.10으로 제한한다. 연령별 시설 배수와 도로망 Min-Max 정규화는 제거했다. 자세한 근거·한계는 [계산 설명](route-age-weights.md), 현장 검증은 [정책 비교 계획](route-weight-evaluation.md)을 참고한다.

기존 DB는 `09_route_age_weights.sql` → `10_common_route_weights.sql` → **`11_police_route_weights.sql`을 실행하고 백엔드·앱을 함께 배포**한다. 이미 10까지 적용했으면 11만 실행한다. 각 정책 전환은 이전 값을 백업한 뒤 최초 1회 적용하고 재실행으로 조정값을 덮어쓰지 않는다. 아래 09 설명은 연령 정보 구조를 추가한 이전 단계에 해당한다.

## 경로 연령대별 가중치 (2026-11-13)

기존 DB에는 백엔드 배포 전에 `09_route_age_weights.sql`을 실행한다. `08_sos_location_log.sql`과 별개이며, 데이터 삭제 없이 `users.birth_date`, `route.safety_age_band`, `route_facility_weight`를 추가한다. 여러 번 실행해도 이미 조정한 가중치를 덮어쓰지 않는다.

- 빈 DB: `01_full_schema.sql` → `03_seed_base.sql` → (선택) `04_enable_rls.sql`.
- 기존 v3 DB: `09_route_age_weights.sql` → `10_common_route_weights.sql` → `11_police_route_weights.sql` → 백엔드·앱 배포.
- v1에서 처음 이전: `02_migrate_supabase_v1_to_v3.sql` → `09_route_age_weights.sql` → `10_common_route_weights.sql` → `11_police_route_weights.sql` → `03_seed_base.sql` → (선택) `04_enable_rls.sql`.

경로 가중치는 `UNDER_14`(만 14세 미만), `AGE_14_PLUS`(만 14세 이상)의 두 종류다. 서버가 한국 날짜와 생년월일로 계산하며, `ADULT/MINOR` 회원 유형 및 그 보호자 규칙은 독립적으로 유지한다. 기존 계정의 생년월일과 기존 경로의 연령대는 알 수 없으므로 NULL로 남긴다.

API·앱 흐름과 초기 가중치는 [경로 연령 가중치 설명](route-age-weights.md)을 참고한다.

## SOS 위치 로그 (2026-11-12)

기존 PostgreSQL DB에는 백엔드 배포 전에 `08_sos_location_log.sql`을 실행한다. 빈 DB를 `01_full_schema.sql`로 새로 만들면 `sos_log`가 포함되어 있으므로 `08`은 필요하지 않다. 이미 `sos_log`가 있는 DB에도 `08`은 누락된 기록 시각, 세션 ID, 정확도 컬럼과 인덱스를 추가한다.

평상시 위치 공유 이력은 Redis의 최근 60분 세션 트랙에 저장된다. SOS가 시작되면 해당 사용자의 Redis 최근 기록과 최신 위치를 `sos_log`에 복사하고, SOS 중 새 위치 기록은 PostgreSQL에 직접 저장한다. Redis에는 실시간 조회용 최신 위치만 유지한다. `sos_log`는 `emergency_id`, `session_id`, `recorded_at` 조합으로 중복 적재를 방지한다.

ERD 3차 확정본(테이블 20개)을 PostgreSQL DDL로 옮긴 파일입니다.
로컬 PostgreSQL 16에서 아래 두 경로를 모두 실행했고, 결과 스키마가 서로 같다는 것과 `05_constraint_tests.sql`의 34개 검사가 통과하는 것을 확인했습니다.

| 파일 | 용도 |
|---|---|
| `01_full_schema.sql` | 빈 DB에 24개 테이블을 처음부터 생성 (공통 시설 정책 전환 이력 포함) |
| `02_migrate_supabase_v1_to_v3.sql` | 지금 Supabase(v1 테이블 10개)를 v3로 변경 |
| `03_seed_base.sql` | 사용자 유형(ADULT/MINOR), 시설 유형 코드 입력. 여러 번 실행해도 안전 |
| `04_enable_rls.sql` | (선택) Supabase REST API로 테이블을 읽지 못하게 RLS 켜기 |
| `05_constraint_tests.sql` | 제약조건 검사. 테스트용 DB에서만 실행 (끝나면 ROLLBACK) |
| `06_backfill_primary_guardian.sql` | 기존 사용자에게 대표 보호자 지정 (2026-10-20, 1회) |
| `07_emergency_sos_20261026.sql` | SOS 기능용 컬럼·CHECK 추가 (2026-10-26, Supabase 적용 완료, 기록용) |
| `08_wearable_connection_20261111.sql` | 워치 연결 인덱스·컬럼 설명 (2026-11-11, **Supabase 적용 필요**) |
| `09_route_age_weights.sql` | 생년월일, 경로 연령대 스냅샷, 14세 기준 시설 가중치 추가 (기존 DB 배포 전 실행) |
| `10_common_route_weights.sql` | 이전 설정 백업 후 연령 공통 시설 가중치로 전환, 전환 이력 기록 |
| `11_police_route_weights.sql` | 경찰시설 0.04를 포함하도록 공통 예산 재배분, 이전 값 백업 |
| `build/` | `02`를 `01`에서 생성하는 스크립트. `01`의 CREATE 문을 고치면 다시 실행 |

## Supabase 적용 순서

1. Supabase 대시보드에서 백업이 있는지 확인합니다.
2. SQL Editor에서 `02_migrate_supabase_v1_to_v3.sql` 실행 후 `09_route_age_weights.sql`, `10_common_route_weights.sql`, `11_police_route_weights.sql` 순서로 실행
   - `route`, `route_point`, `route_segment`, `safety_facility`, `segment_facility`에 데이터가 있으면 아무것도 바꾸지 않고 중단합니다.
   - 새로 만들 테이블이 이미 있어도 중단합니다.
   - 한 트랜잭션이라 중간에 실패하면 전부 취소됩니다.
3. `03_seed_base.sql` 실행
4. (선택) `04_enable_rls.sql` 실행 — 파일 안의 주의 사항을 먼저 읽어 주세요.
5. 백엔드 `bootRun`으로 `ddl-auto=validate` 통과 확인

## 새 DB(로컬 개발 등)

`01_full_schema.sql` → `03_seed_base.sql` → (선택) `04_enable_rls.sql`

## 02 다시 만들기

```bash
python3 build/build_migration.py .
```

## 2026-10-26 변경

- `emergency_event.resolved_by_user_id` 추가 (FK → users, 종료한 계정, 자동 종료면 NULL), `emergency_event_log.event_type` CHECK 확정 → `07`로 Supabase 적용 완료. `01`에도 반영.
- `road_image_analysis` (AI 도로 이미지 분석 결과): 팀에서 Supabase에 직접 생성. 다른 테이블과 FK 없음, 백엔드 엔티티 없음. `01` 8절에 컬럼·타입만 옮겨 둠 (NULL 허용·기본값은 실제 테이블과 다를 수 있음).
- `road_image_analysis`는 `04_enable_rls.sql`에 넣지 않았음. AI 쪽이 Supabase REST API(anon 키)로 이 테이블을 읽고 쓴다면 RLS를 켜는 순간 막히므로, 접근 방식 확인 후 결정.
- `02`는 v1 → v3 전환용이라 다시 만들지 않음. `build/`로 `02`를 재생성하면 이번 변경이 섞일 수 있으니 실행하지 않음.

## 2026-11-11 변경 (워치 ↔ 계정 연결)

- `wearable_device`에 `uq_wearable_identifier_connected (device_identifier) WHERE is_active` 추가 → 워치 1대는 한 계정에서만 연결 중.
  같은 워치를 여러 계정에 **등록**하는 것은 계속 허용 (`uq_wearable_identifier_active`는 그대로 사용자 + 식별자).
- 컬럼 의미 확정 (COMMENT): `is_active` = 현재 앱과 연결 중인 기기(사용자당 1대, 워치당 1계정),
  `last_connected_at` = 마지막 연결 상태 변경 시각, `device_identifier` = 워치 ANDROID_ID.
- **Supabase 적용:** SQL Editor에서 `08_wearable_connection_20261111.sql` 실행 → 백엔드 재시작.
  같은 워치가 여러 계정에서 연결 중으로 남아 있으면 가장 최근 것만 남기고 해제한 뒤 인덱스를 만든다. 여러 번 실행해도 안전.
- 이전 워치 앱(설치 UUID)으로 등록한 테스트 기기는 ANDROID_ID와 값이 달라 새 기기로 보인다 → 앱에서 삭제 후 다시 등록.
