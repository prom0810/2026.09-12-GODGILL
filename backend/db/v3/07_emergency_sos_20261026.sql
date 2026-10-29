-- =====================================================================
-- GODGILL DB v3 — 2026-10-26 변경분 (Supabase에 이미 적용됨, 기록용)
--
-- SOS · 보호자 위치 공유 기능 (claude/emergency-sos-api-v1.md)
--   - emergency_event.resolved_by_user_id: 종료한 계정(사용자 본인 또는 보호자), 자동 종료면 NULL
--   - emergency_event_log.event_type 값 목록 CHECK 확정
--
-- 참고: road_image_analysis 테이블은 팀에서 Supabase에 직접 만들었으므로 이 파일에 없음
--       (빈 DB용 정의는 01_full_schema.sql 8절).
--
-- Supabase에는 FK·CHECK 이름 없이 적용되어 자동 이름(emergency_event_resolved_by_user_id_fkey 등)이
-- 붙어 있을 수 있음. 01은 이름을 fk_emergency_resolved_by로 지정. 동작은 같음.
-- 빈 DB(01로 생성)에는 실행하지 않음 — 이미 포함되어 있음.
-- =====================================================================

BEGIN;

ALTER TABLE emergency_event
    ADD COLUMN resolved_by_user_id BIGINT NULL;
ALTER TABLE emergency_event
    ADD CONSTRAINT fk_emergency_resolved_by FOREIGN KEY (resolved_by_user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT;
ALTER TABLE emergency_event
    ADD CONSTRAINT ck_emergency_resolved_by
        CHECK (resolved_by_user_id IS NULL OR resolved_at IS NOT NULL);
COMMENT ON COLUMN emergency_event.resolved_by_user_id IS '종료한 계정(사용자 본인 또는 CONNECTED 보호자). 자동 종료(AUTO_CLOSED)면 NULL';

ALTER TABLE emergency_event_log
    ADD CONSTRAINT ck_emergency_log_event_type
        CHECK (event_type IN ('CREATED', 'GUARDIAN_PUSHED', 'GUARDIAN_SMS_SENT',
                              'LOCATION_LOST', 'RESOLVED', 'FALSE_ALARM', 'CANCELLED', 'AUTO_CLOSED'));

COMMIT;
