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

-- 기존 회원 유형별 facility_type_weight는 현재 경로 계산에서 사용하지 않는다.
-- 실제 경로 연령대별 가중치는 아래 route_facility_weight에 입력한다.
-- 예시 (ERD 메모의 임시값, 확정 아님):
-- INSERT INTO facility_type_weight (facility_type_id, user_type_id, weight)
-- SELECT f.facility_type_id, u.user_type_id, w.weight
-- FROM (VALUES ('CCTV', 0.30), ('EMERGENCY_BELL', 0.25), ('POLICE', 0.35), ('STREET_LIGHT', 0.20)) AS w(type_name, weight)
-- JOIN safety_facility_type f ON f.type_name = w.type_name
-- CROSS JOIN user_type u
-- ON CONFLICT (facility_type_id, user_type_id) DO UPDATE SET weight = EXCLUDED.weight;

-- 경로 가중치 초기값. 기존 회원 유형별 facility_type_weight와 별개로 사용한다.
-- 수치는 서비스 초기 설정이며, 재실행해도 조정된 값을 덮어쓰지 않는다.
INSERT INTO route_facility_weight (age_band, facility_type, weight) VALUES
    ('UNDER_14', 'CCTV', 0.02), ('UNDER_14', 'STREET_LIGHT', 0.04),
    ('UNDER_14', 'POLICE', 0.04), ('UNDER_14', 'EMERGENCY_BELL', 0.00),
    ('UNDER_14', 'CONVENIENCE_STORE', 0.00), ('UNDER_14', 'FIRE_STATION', 0.00),
    ('AGE_14_PLUS', 'CCTV', 0.02), ('AGE_14_PLUS', 'STREET_LIGHT', 0.04),
    ('AGE_14_PLUS', 'POLICE', 0.04), ('AGE_14_PLUS', 'EMERGENCY_BELL', 0.00),
    ('AGE_14_PLUS', 'CONVENIENCE_STORE', 0.00), ('AGE_14_PLUS', 'FIRE_STATION', 0.00)
ON CONFLICT (age_band, facility_type) DO NOTHING;

COMMIT;
