-- =====================================================================
-- GODGILL DB v3 — 기본 코드 데이터 (여러 번 실행해도 안전)
-- 01 또는 02 실행 후에 실행합니다.
-- =====================================================================

BEGIN;

INSERT INTO user_type (type_name) VALUES
    ('ADULT'),
    ('MINOR')
ON CONFLICT (type_name) DO NOTHING;

-- 시설 유형 코드 (v1 Supabase에 이미 들어 있는 코드와 동일).
-- 값은 segment_facility.facility_type에 그대로 복사되므로 영문 코드로 유지합니다.
INSERT INTO safety_facility_type (type_name, description) VALUES
    ('CCTV',           '방범 및 안전 감시용 CCTV'),
    ('EMERGENCY_BELL', '안전 비상벨'),
    ('POLICE',         '경찰서, 지구대 등 경찰시설'),
    ('STREET_LIGHT',   '가로등')
ON CONFLICT (type_name) DO NOTHING;

-- 가중치(facility_type_weight)는 알고리즘 확정 후 입력합니다.
-- 예시 (ERD 메모의 임시값, 확정 아님):
-- INSERT INTO facility_type_weight (facility_type_id, user_type_id, weight)
-- SELECT f.facility_type_id, u.user_type_id, w.weight
-- FROM (VALUES ('CCTV', 0.30), ('EMERGENCY_BELL', 0.25), ('POLICE', 0.35), ('STREET_LIGHT', 0.20)) AS w(type_name, weight)
-- JOIN safety_facility_type f ON f.type_name = w.type_name
-- CROSS JOIN user_type u
-- ON CONFLICT (facility_type_id, user_type_id) DO UPDATE SET weight = EXCLUDED.weight;

COMMIT;
