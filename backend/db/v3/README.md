# GODGILL DB 스키마 v3 (2026-11-16 갱신)

현재 `01_full_schema.sql`은 경로 가중치, SOS 위치 로그, SOS 심박 기록을 포함해 **25개 테이블**을 생성한다. 새 DB는 전체 스키마로 생성하고, 기존 DB는 적용하지 않은 변경 파일만 실행한다.

## 공통 시설 정책으로 전환

현재 정책은 두 연령대에 CCTV 0.02·가로등 0.04·경찰시설 0.04·나머지 0을 적용하고 전체 보너스를 0.10으로 제한한다. 연령별 시설 배수와 도로망 Min-Max 정규화는 제거했다. 자세한 근거·한계는 [계산 설명](route-age-weights.md), 현장 검증은 [정책 비교 계획](route-weight-evaluation.md)을 참고한다.

기존 DB의 경로 가중치 적용 순서는 `09_route_age_weights.sql` → `10_common_route_weights.sql` → **`11_police_route_weights.sql`**이다. 이미 10까지 적용했으면 11만 실행한다. 10과 11은 각각 이전 값을 `route_weight_policy_history`에 백업한 뒤 최초 1회 정책을 적용하며, 재실행으로 조정값을 덮어쓰지 않는다. 백엔드와 앱을 함께 배포한다.

| 시설 | 만 14세 미만 | 만 14세 이상 |
|---|---:|---:|
| 방범 목적 CCTV | 0.02 | 0.02 |
| 가로등 | 0.04 | 0.04 |
| 경찰시설 | 0.04 | 0.04 |
| 비상벨·편의점·소방서 | 0 | 0 |

각 가중치에는 시설까지의 거리와 위험구역 감점에 따른 감소 계수를 곱한다. 현재는 두 연령대에 같은 값을 적용하며, 이 수치는 검증된 안전 확률이 아닌 서비스의 초기 정책값이다. `09_route_age_weights.sql`의 과거 연령별 값으로 배포하지 않도록 11까지 적용한다.

## 경로 연령대별 가중치 (2026-11-13)

`09_route_age_weights.sql`은 `08_sos_location_log.sql`과 별개이며, 데이터 삭제 없이 `users.birth_date`, `route.safety_age_band`, `route_facility_weight`를 추가한다. 여러 번 실행해도 이미 조정한 가중치를 덮어쓰지 않는다. 현재 공통 가중치로 전환하려면 이어서 10과 11을 실행한다.

경로 가중치는 `UNDER_14`(만 14세 미만), `AGE_14_PLUS`(만 14세 이상)의 두 종류다. 서버가 한국 날짜와 생년월일로 계산하며, `ADULT/MINOR` 회원 유형 및 그 보호자 규칙은 독립적으로 유지한다. 기존 계정의 생년월일과 기존 경로의 연령대는 알 수 없으므로 NULL로 남긴다.

API·앱 흐름과 현재 계산식은 [경로 연령 가중치 설명](route-age-weights.md)을 참고한다.

## SOS 위치 로그 (2026-11-12)

기존 PostgreSQL DB에는 백엔드 배포 전에 `08_sos_location_log.sql`을 실행한다. 빈 DB를 `01_full_schema.sql`로 새로 만들면 `sos_log`가 포함되어 있으므로 `08`은 필요하지 않다. 이미 `sos_log`가 있는 DB에도 `08`은 누락된 기록 시각, 세션 ID, 정확도 컬럼과 인덱스를 추가한다.

평상시 위치 공유 이력은 Redis의 최근 60분 세션 트랙에 저장된다. SOS가 시작되면 해당 사용자의 Redis 최근 기록과 최신 위치를 `sos_log`에 복사하고, SOS 중 새 위치 기록은 PostgreSQL에 직접 저장한다. Redis에는 실시간 조회용 최신 위치만 유지한다. `sos_log`는 `emergency_id`, `session_id`, `recorded_at` 조합으로 중복 적재를 방지한다.

## 파일 안내

| 파일 | 용도 |
|---|---|
| `01_full_schema.sql` | 빈 DB에 25개 테이블 생성 (SOS 위치·심박 기록, 경로 가중치·정책 전환 이력 포함) |
| `02_migrate_supabase_v1_to_v3.sql` | v1 테이블 10개를 2026-10-15 기준 v3로 변경. 이후 변경은 별도 적용 |
| `03_seed_base.sql` | 사용자 유형(ADULT/MINOR), 시설 유형 코드, 현재 공통 경로 가중치 입력. 기존 값은 유지 |
| `04_enable_rls.sql` | (선택) Supabase REST API로 테이블을 읽지 못하게 RLS 켜기 |
| `05_constraint_tests.sql` | 제약조건 검사. 테스트용 DB에서만 실행 (끝나면 ROLLBACK) |
| `06_backfill_primary_guardian.sql` | 기존 사용자에게 대표 보호자 지정 (2026-10-20, 1회) |
| `07_emergency_sos_20261026.sql` | SOS 기능용 컬럼·CHECK 추가 (2026-10-26, Supabase 적용 완료, 기록용) |
| `08_wearable_connection_20261111.sql` | 워치 연결 인덱스·컬럼 설명 (2026-11-11, **Supabase 적용 필요**) |
| `08_sos_location_log.sql` | SOS 위치 로그 생성 및 세션 ID·기록 시각·정확도·인덱스 추가 |
| `09_erd_20261113.sql` | `bpm_log` 추가, `emergency_event.lat/lng`를 `sos_log`로 옮긴 후 삭제. 해당 백엔드와 함께 적용 |
| `09_route_age_weights.sql` | 생년월일, 경로 연령대 스냅샷, 과거 연령별 시설 가중치 추가. 이어서 10·11 적용 |
| `10_common_route_weights.sql` | 이전 설정 백업 후 연령 공통 시설 가중치로 전환, 전환 이력 기록 |
| `11_police_route_weights.sql` | 경찰시설 0.04를 포함하도록 공통 예산 재배분, 이전 값 백업 |
| `tests/route_age_weights.sql` | 생년월일·경로 연령대·시설 가중치 제약 검사. 테스트 DB에서 실행, 마지막에 ROLLBACK |
| `tests/common_route_weights.sql` | 현재 초기 가중치·연령대별 합계 검사. 초기값 상태의 테스트 DB에서 실행, 마지막에 ROLLBACK |
| `route-age-weights.md` | 경로 가중치 계산식, API·앱 흐름, 정책의 근거와 한계 |
| `route-weight-evaluation.md` | 공통 정책·과거 정책·향후 연령 보정 정책 비교 계획 |
| `build/` | `02`를 `01`에서 생성하는 도구. 현재 `02`는 과거 v1 전환용이므로 자동 재생성하지 않음 |

## 기존 v3 DB 적용 순서

1. Supabase 대시보드에서 백업과 기존 적용 내역을 확인한다. 이미 v3인 DB에는 `02_migrate_supabase_v1_to_v3.sql`을 실행하지 않는다.
2. 워치 연결 변경이 누락되어 있으면 `08_wearable_connection_20261111.sql`을 실행한다.
3. `08_sos_location_log.sql` → `09_erd_20261113.sql` 순서로 SOS 변경을 적용한다. 이미 적용한 파일은 생략한다.
4. `09_route_age_weights.sql` → `10_common_route_weights.sql` → `11_police_route_weights.sql` 순서로 경로 변경을 적용한다. 이미 적용한 단계는 생략한다.
5. 필요하면 `03_seed_base.sql`로 누락된 초기값을 넣고, RLS를 사용하는 환경은 `04_enable_rls.sql`의 주의 사항을 확인한 뒤 적용한다.
6. 변경에 맞는 백엔드와 앱을 함께 배포하고, 백엔드의 `ddl-auto=validate` 통과를 확인한다.

`08`과 `09`로 시작하는 파일이 각각 두 개씩 있으므로 **번호만 보지 말고 전체 파일명으로 구분**한다. SOS 변경과 경로 변경은 별개지만, `09_erd_20261113.sql`은 반드시 SOS 위치 로그 생성 이후에 실행해야 한다. `emergency_event.lat/lng`를 매핑하는 이전 백엔드는 해당 컬럼 삭제 후 시작에 실패하므로 배포 시점을 맞춘다.

## v1 DB에서 처음 이전하는 경우

`02_migrate_supabase_v1_to_v3.sql` → `09_route_age_weights.sql` → `03_seed_base.sql` → `06_backfill_primary_guardian.sql`을 실행한 뒤, 누락된 `07_emergency_sos_20261026.sql`과 위의 기존 v3 변경을 적용한다. 현재 `03`은 `route_facility_weight`에도 값을 넣으므로, 이 테이블을 만드는 `09_route_age_weights.sql`을 먼저 실행해야 한다. 경로 정책 전환은 10과 11까지 완료한다.

`02`는 2026-10-15 기준의 전환 스크립트다. 삭제 후 재생성하는 `route`, `route_point`, `route_segment`, `safety_facility`, `segment_facility`에 데이터가 있거나 새로 만들 테이블이 이미 있으면 중단한다. 한 트랜잭션이므로 중간 실패 시 해당 파일의 변경은 취소된다. `07`은 재실행용 파일이 아니므로 적용 여부를 확인한다. `road_image_analysis`는 `02`에 포함되지 않으며, 필요하면 `01`의 해당 정의를 별도로 적용한다.

## 새 DB(로컬 개발 등)

`01_full_schema.sql` → `03_seed_base.sql` → (선택) `04_enable_rls.sql`

전체 스키마에 최신 변경이 포함되어 있으므로 새 DB에는 06~11 변경 파일을 추가로 실행할 필요가 없다. 초기값 상태의 테스트 DB에서 `05_constraint_tests.sql`, `tests/route_age_weights.sql`, `tests/common_route_weights.sql`을 실행해 제약과 가중치를 확인할 수 있다.

## 02 생성 도구

아래 명령은 `01`에서 `02`를 생성하는 도구다. 현재 `02`는 과거 v1 DB 구조를 기준으로 유지하므로, `01`을 수정했다고 바로 실행하지 않는다. 재생성하려면 v1 전환 조건과 후속 변경 파일의 중복 적용 여부를 먼저 검토한다.

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

## 2026-11-13 변경 (SOS 심박 기록, SOS 위치 → sos_log)

- `bpm_log` 추가: 심박 이상 SOS(`emergency_event.type = ABNORMAL_HEART_RATE`)의 발생 당시(`bpm_start`)·종료 시(`bpm_end`) 심박. 사건당 1행(`uq_bpm_log_emergency`), SOS 종료 때 백엔드가 저장.
- 낙상 SOS·직접 누른 SOS는 센서 값을 저장하지 않는다. `heart_rate_record`, `motion_record`는 용도 미정으로 보류(테이블은 남겨 둠, 백엔드가 쓰지 않음).
- ERD 그림의 PK 이름 `sos_log_id`는 `sos_log` 표에서 복사된 것으로 보고 `bpm_log_id`로 만들었다. 팀이 이미 다른 이름으로 만들었다면 그대로 써도 백엔드는 PK 이름을 쓰지 않아 영향 없음.
- `emergency_event.lat/lng` 삭제: SOS 위치는 `sos_log`(main의 `08_sos_location_log.sql`, 팀원 #40)에만 둔다. 삭제 전에 sos_log에 위치가 없는 사건의 발생 위치를 `session_id = 'sos-' || emergency_id`, `recorded_at = occurred_at`으로 옮긴다. `01`의 emergency_event에서도 lat/lng·관련 CHECK를 뺐고, `05`의 lat/lng 검사를 bpm_log 검사 2개로 바꿨다.
- **Supabase 적용:** SQL Editor에서 `09_erd_20261113.sql` 실행 (한 트랜잭션, 여러 번 실행해도 안전, sos_log가 없으면 아무것도 바꾸지 않고 중단).
- 백엔드(`feature/backend/watch-sos-bpm`)는 lat/lng 매핑을 뺐다: 발생 위치는 sos_log의 `sos-{id}` 세션 첫 위치, 진행 중 위치 대체값은 sos_log 최신 위치.
- **주의:** lat/lng를 매핑하는 이전 백엔드는 `09_erd_20261113.sql` 적용 후 `ddl-auto=validate`에서 시작에 실패한다. 해당 매핑을 제거한 백엔드와 함께 적용한다.
