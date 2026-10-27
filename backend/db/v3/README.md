# GODGILL DB 스키마 v3 (2026-10-15, 2026-10-26 갱신)

ERD 3차 확정본(테이블 20개)을 PostgreSQL DDL로 옮긴 파일입니다.
로컬 PostgreSQL 16에서 아래 두 경로를 모두 실행했고, 결과 스키마가 서로 같다는 것과 `05_constraint_tests.sql`의 33개 검사가 통과하는 것을 확인했습니다.

| 파일 | 용도 |
|---|---|
| `01_full_schema.sql` | 빈 DB에 21개 테이블을 처음부터 생성 (기준 스키마, 2026-10-26 변경 포함) |
| `02_migrate_supabase_v1_to_v3.sql` | 지금 Supabase(v1 테이블 10개)를 v3로 변경 |
| `03_seed_base.sql` | 사용자 유형(ADULT/MINOR), 시설 유형 코드 입력. 여러 번 실행해도 안전 |
| `04_enable_rls.sql` | (선택) Supabase REST API로 테이블을 읽지 못하게 RLS 켜기 |
| `05_constraint_tests.sql` | 제약조건 검사. 테스트용 DB에서만 실행 (끝나면 ROLLBACK) |
| `06_backfill_primary_guardian.sql` | 기존 사용자에게 대표 보호자 지정 (2026-10-20, 1회) |
| `07_emergency_sos_20261026.sql` | SOS 기능용 컬럼·CHECK 추가 (2026-10-26, Supabase 적용 완료, 기록용) |
| `build/` | `02`를 `01`에서 생성하는 스크립트. `01`의 CREATE 문을 고치면 다시 실행 |

## Supabase 적용 순서

1. Supabase 대시보드에서 백업이 있는지 확인합니다.
2. SQL Editor에서 `02_migrate_supabase_v1_to_v3.sql` 실행
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
