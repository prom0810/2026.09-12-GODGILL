-- =====================================================================
-- GODGILL DB v3 — 제약조건 검증 스크립트 (로컬/테스트 DB 전용)
-- 01 + 03 실행 직후의 빈 DB에서 실행합니다. 전체가 ROLLBACK되므로 데이터가 남지 않습니다.
-- 정상 데이터 흐름이 들어가는지 확인한 뒤, 막혀야 하는 입력이 실제로 막히는지 하나씩 확인합니다.
-- 하나라도 예상과 다르면 '[FAIL]' 오류로 중단됩니다.
-- =====================================================================

BEGIN;

-- stmt가 expected(제약 이름 등)를 포함한 오류로 막히는지 확인한다.
CREATE FUNCTION pg_temp.expect_error(label TEXT, expected TEXT, stmt TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE msg TEXT;
BEGIN
    BEGIN
        EXECUTE stmt;
    EXCEPTION WHEN OTHERS THEN
        msg := SQLERRM;
        IF position(expected IN msg) = 0 THEN
            RAISE EXCEPTION '[FAIL] % — 다른 이유로 막힘: %', label, msg;
        END IF;
        RAISE NOTICE '[OK]   %', label;
        RETURN;
    END;
    RAISE EXCEPTION '[FAIL] % — 막혀야 하는데 통과함', label;
END $$;

-- ---------------------------------------------------------------------
-- 1. 정상 흐름: 가입 → 보호자 → 워치 → 경로 → 센서 → 긴급 사건 → 제보 승인
-- ---------------------------------------------------------------------

INSERT INTO users (user_id, user_type_id, name, email, password, phone)
SELECT 1, user_type_id, '김성인', 'adult@test.com', 'hash', '010-1111-1111' FROM user_type WHERE type_name = 'ADULT';
INSERT INTO users (user_id, user_type_id, name, email, password, phone)
SELECT 2, user_type_id, '이학생', 'minor@test.com', 'hash', '010-2222-2222' FROM user_type WHERE type_name = 'MINOR';

INSERT INTO guardian (guardian_id, user_id, name, phone, relationship, is_primary)
VALUES (1, 2, '이엄마', '010-3333-3333', 'PARENT', TRUE);
UPDATE guardian SET guardian_user_id = 1, registration_status = 'CONNECTED', connected_at = now()
WHERE guardian_id = 1;

INSERT INTO wearable_device (wearable_id, user_id, device_type, device_identifier)
VALUES (1, 2, 'WEAR_OS', 'a1b2c3d4e5f67890');
UPDATE wearable_device SET is_active = TRUE WHERE wearable_id = 1;

INSERT INTO route (route_id, user_id, user_type_id, total_distance, estimated_time, safety_score)
SELECT 1, 2, user_type_id, 820.5, 660, 7.8 FROM user_type WHERE type_name = 'MINOR';
INSERT INTO route_point (point_id, route_id, seq, point_type, lat, lng) VALUES
    (1, 1, 0, 'START',    36.8090, 127.1460),
    (2, 1, 1, 'WAYPOINT', 36.8100, 127.1470),
    (3, 1, 2, 'END',      36.8110, 127.1480);
INSERT INTO route_segment (segment_id, start_point_id, end_point_id, distance, safety_score, risk_score) VALUES
    (1, 1, 2, 410.0, 7.5, 1.2),
    (2, 2, 3, 410.5, 8.1, 0.8);
INSERT INTO segment_facility (segment_id, facility_type, lat, lng, quantity, distance, applied_score)
VALUES (1, 'CCTV', 36.8095, 127.1462, 2, 12.4, 0.60);

INSERT INTO heart_rate_record (wearable_id, user_id, segment_id, start_distance, end_distance,
                               avg_bpm, min_bpm, max_bpm, sample_count, started_at, ended_at)
VALUES (1, 2, 1, 0, 100, 87, 79, 102, 28, now() - interval '2 min', now());
INSERT INTO motion_record (user_id, wearable_id, segment_id, start_distance, end_distance,
                           avg_acceleration, max_acceleration, avg_gyro, max_gyro, sample_count, started_at, ended_at)
VALUES (2, 1, 1, 0, 100, 9.92, 13.41, 0.32, 1.18, 1180, now() - interval '2 min', now());

INSERT INTO emergency_event (emergency_id, user_id, type, source, lat, lng)
VALUES (1, 2, 'SOS', 'MOBILE', 36.8095, 127.1462);
INSERT INTO emergency_event_log (emergency_id, event_type, message) VALUES (1, 'CREATED', 'SOS 버튼');
UPDATE emergency_event SET status = 'RESOLVED', resolved_at = now() WHERE emergency_id = 1;
INSERT INTO emergency_event (emergency_id, user_id, wearable_id, route_id, segment_id, type, source)
VALUES (2, 2, 1, 1, 1, 'FALL', 'SYSTEM');

INSERT INTO risk_report (report_id, user_id, risk_type, lat, lng) VALUES (1, 1, 'DARK_ROAD', 36.81, 127.15);
INSERT INTO risk_area (risk_area_id, risk_type, lat, lng, radius) VALUES (1, 'DARK_ROAD', 36.81, 127.15, 30);
UPDATE risk_report SET status = 'APPROVED', risk_area_id = 1, updated_at = now() WHERE report_id = 1;
INSERT INTO risk_report_image (report_id, storage_path) VALUES (1, 'reports/1/a.jpg');

INSERT INTO notification (user_id, type, title, content, reference_type, reference_id)
VALUES (1, 'EMERGENCY', '긴급 상황', '이학생 님에게 낙상이 감지됐습니다.', 'EMERGENCY_EVENT', 2);

UPDATE route SET status = 'COMPLETED', completed_at = now() WHERE route_id = 1;

-- 위에서 PK를 직접 넣었으므로 IDENTITY·SERIAL 시퀀스를 현재 최댓값 뒤로 맞춘다
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT table_name, column_name FROM information_schema.columns
             WHERE table_schema = 'public' AND (is_identity = 'YES' OR column_default LIKE 'nextval%') LOOP
        EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, %L), coalesce((SELECT max(%I) FROM %I), 0) + 1, false)',
                       r.table_name, r.column_name, r.column_name, r.table_name);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 2. 막혀야 하는 입력
-- ---------------------------------------------------------------------

DO $do$ BEGIN
    PERFORM pg_temp.expect_error('users: 전화번호 중복', 'phone',
      $q$INSERT INTO users (user_type_id, name, email, password, phone) SELECT user_type_id, 'x', 'x@test.com', 'h', '010-1111-1111' FROM user_type LIMIT 1$q$);
    PERFORM pg_temp.expect_error('users: 인증 여부와 인증 시각 불일치', 'ck_users_identity_verified',
      $q$UPDATE users SET identity_verified = TRUE WHERE user_id = 1$q$);

    PERFORM pg_temp.expect_error('guardian: 관계 코드 외 값', 'ck_guardian_relationship',
      $q$INSERT INTO guardian (user_id, name, phone, relationship) VALUES (2, 'x', '010-5555-5555', '엄마')$q$);
    PERFORM pg_temp.expect_error('guardian: 같은 번호 중복 (삭제 안 된 보호자)', 'uq_guardian_user_phone_active',
      $q$INSERT INTO guardian (user_id, name, phone, relationship) VALUES (2, 'x', '010-3333-3333', 'FAMILY')$q$);
    PERFORM pg_temp.expect_error('guardian: 대표 보호자 2명', 'uq_guardian_primary_active',
      $q$INSERT INTO guardian (user_id, name, phone, relationship, is_primary) VALUES (2, 'x', '010-6666-6666', 'FAMILY', TRUE)$q$);
    PERFORM pg_temp.expect_error('guardian: 본인을 보호자 계정으로 연결', 'ck_guardian_not_self',
      $q$INSERT INTO guardian (user_id, guardian_user_id, name, phone, relationship) VALUES (2, 2, 'x', '010-7777-7777', 'FAMILY')$q$);
    PERFORM pg_temp.expect_error('guardian: CONNECTED인데 연결 계정 없음', 'ck_guardian_connected',
      $q$INSERT INTO guardian (user_id, name, phone, relationship, registration_status) VALUES (2, 'x', '010-8888-8888', 'FAMILY', 'CONNECTED')$q$);

    PERFORM pg_temp.expect_error('wearable: 같은 워치 중복 등록', 'uq_wearable_identifier_active',
      $q$INSERT INTO wearable_device (user_id, device_type, device_identifier) VALUES (2, 'WEAR_OS', 'a1b2c3d4e5f67890')$q$);
    PERFORM pg_temp.expect_error('wearable: 사용 중 워치 2대', 'uq_wearable_one_active',
      $q$INSERT INTO wearable_device (user_id, device_type, device_identifier, is_active) VALUES (2, 'CUSTOM', '24:6F:28:AB:CD:EF', TRUE)$q$);
    PERFORM pg_temp.expect_error('wearable: 삭제된 워치가 사용 중', 'ck_wearable_deleted_inactive',
      $q$UPDATE wearable_device SET deleted_at = now() WHERE wearable_id = 1$q$);
    PERFORM pg_temp.expect_error('wearable: 기기 종류 코드 외 값', 'ck_wearable_device_type',
      $q$INSERT INTO wearable_device (user_id, device_type, device_identifier) VALUES (1, 'IPHONE', 'x')$q$);

    PERFORM pg_temp.expect_error('route: COMPLETED인데 완료 시각 없음', 'ck_route_completed',
      $q$INSERT INTO route (user_id, user_type_id, total_distance, estimated_time, safety_score, status) SELECT 1, user_type_id, 1, 1, 1, 'COMPLETED' FROM user_type LIMIT 1$q$);
    PERFORM pg_temp.expect_error('route_point: 같은 순번 중복', 'uq_route_point_seq',
      $q$INSERT INTO route_point (route_id, seq, point_type, lat, lng) VALUES (1, 1, 'WAYPOINT', 36.8, 127.1)$q$);
    PERFORM pg_temp.expect_error('route_point: START 2개', 'uq_route_point_start',
      $q$INSERT INTO route_point (route_id, seq, point_type, lat, lng) VALUES (1, 9, 'START', 36.8, 127.1)$q$);
    PERFORM pg_temp.expect_error('route_segment: 시작·종료 지점 같음', 'ck_segment_points_differ',
      $q$INSERT INTO route_segment (start_point_id, end_point_id, distance, safety_score, risk_score) VALUES (3, 3, 1, 1, 1)$q$);
    PERFORM pg_temp.expect_error('route_segment: 한 지점에서 구간 2개 시작', 'uq_segment_start_point',
      $q$INSERT INTO route_segment (start_point_id, end_point_id, distance, safety_score, risk_score) VALUES (1, 3, 1, 1, 1)$q$);
    PERFORM pg_temp.expect_error('route: 센서 기록이 있는 경로 삭제 (RESTRICT)', 'foreign key',
      $q$DELETE FROM route WHERE route_id = 1$q$);

    PERFORM pg_temp.expect_error('heart_rate: min > avg', 'ck_heart_rate_bpm_order',
      $q$INSERT INTO heart_rate_record (wearable_id, user_id, segment_id, start_distance, end_distance, avg_bpm, min_bpm, max_bpm, sample_count, started_at, ended_at) VALUES (1, 2, 1, 0, 10, 80, 90, 100, 5, now(), now())$q$);
    PERFORM pg_temp.expect_error('heart_rate: 종료 시각이 시작보다 빠름', 'ck_heart_rate_time',
      $q$INSERT INTO heart_rate_record (wearable_id, user_id, segment_id, start_distance, end_distance, avg_bpm, min_bpm, max_bpm, sample_count, started_at, ended_at) VALUES (1, 2, 1, 0, 10, 80, 70, 100, 5, now(), now() - interval '1 min')$q$);
    PERFORM pg_temp.expect_error('motion: 동작 코드 외 값', 'ck_motion_event_type',
      $q$INSERT INTO motion_record (user_id, wearable_id, segment_id, start_distance, end_distance, event_type, avg_acceleration, max_acceleration, avg_gyro, max_gyro, sample_count, started_at, ended_at) VALUES (2, 1, 1, 0, 10, 'RUNNING', 1, 2, 1, 2, 5, now(), now())$q$);

    PERFORM pg_temp.expect_error('emergency: 자동 감지인데 워치·구간 없음', 'ck_emergency_auto_detect_context',
      $q$INSERT INTO emergency_event (user_id, type, source) VALUES (1, 'FALL', 'SYSTEM')$q$);
    PERFORM pg_temp.expect_error('emergency: 진행 중 사건 중복', 'uq_emergency_one_open',
      $q$INSERT INTO emergency_event (user_id, type, source) VALUES (2, 'SOS', 'MOBILE')$q$);
    PERFORM pg_temp.expect_error('emergency: 종료 상태인데 종료 시각 없음', 'ck_emergency_resolved_when_closed',
      $q$UPDATE emergency_event SET status = 'RESOLVED' WHERE emergency_id = 2$q$);
    PERFORM pg_temp.expect_error('emergency: 위도만 있고 경도 없음', 'ck_emergency_lat_lng_pair',
      $q$INSERT INTO emergency_event (user_id, type, source, lat) VALUES (1, 'SOS', 'MOBILE', 36.8)$q$);

    PERFORM pg_temp.expect_error('risk_report: 승인인데 위험구역 없음', 'ck_risk_report_area_when_approved',
      $q$INSERT INTO risk_report (user_id, risk_type, lat, lng, status) VALUES (1, 'DARK_ROAD', 36.8, 127.1, 'APPROVED')$q$);
    PERFORM pg_temp.expect_error('risk_report: 검토 전인데 위험구역 연결', 'ck_risk_report_area_when_approved',
      $q$INSERT INTO risk_report (user_id, risk_type, lat, lng, risk_area_id) VALUES (1, 'DARK_ROAD', 36.8, 127.1, 1)$q$);
    PERFORM pg_temp.expect_error('risk_report: 상태만 먼저 APPROVED로 변경', 'ck_risk_report_area_when_approved',
      $q$INSERT INTO risk_report (report_id, user_id, risk_type, lat, lng) VALUES (99, 1, 'OTHER', 36.8, 127.1); UPDATE risk_report SET status = 'APPROVED' WHERE report_id = 99$q$);
    PERFORM pg_temp.expect_error('risk_area: 제보가 연결된 구역 삭제 (RESTRICT)', 'fk_risk_report_area',
      $q$DELETE FROM risk_area WHERE risk_area_id = 1$q$);
    PERFORM pg_temp.expect_error('risk_area: 반경 0', 'ck_risk_area_radius',
      $q$INSERT INTO risk_area (risk_type, lat, lng, radius) VALUES ('OTHER', 36.8, 127.1, 0)$q$);

    PERFORM pg_temp.expect_error('facility_type_weight: 음수 가중치', 'ck_ftw_weight',
      $q$INSERT INTO facility_type_weight (facility_type_id, user_type_id, weight) SELECT f.facility_type_id, u.user_type_id, -0.1 FROM safety_facility_type f, user_type u LIMIT 1$q$);
    PERFORM pg_temp.expect_error('safety_facility: 설치 개수 0', 'ck_safety_facility_quantity',
      $q$INSERT INTO safety_facility (facility_type_id, quantity, lat, lng, source) SELECT facility_type_id, 0, 36.8, 127.1, 'TEST' FROM safety_facility_type LIMIT 1$q$);

    PERFORM pg_temp.expect_error('notification: reference_type만 있고 id 없음', 'ck_notification_reference_pair',
      $q$INSERT INTO notification (user_id, type, title, content, reference_type) VALUES (1, 'GUARDIAN', 't', 'c', 'GUARDIAN')$q$);
END $do$;

-- ---------------------------------------------------------------------
-- 3. CASCADE 확인: 센서·긴급 기록을 지우면 경로 삭제 시 지점·구간·시설 스냅샷이 함께 삭제됨
-- ---------------------------------------------------------------------

DELETE FROM heart_rate_record; DELETE FROM motion_record;
DELETE FROM emergency_event WHERE emergency_id = 2;
DELETE FROM notification;
UPDATE route SET status = 'CANCELLED', completed_at = NULL WHERE route_id = 1;
DELETE FROM route WHERE route_id = 1;
DO $$ BEGIN
    IF (SELECT count(*) FROM route_point) + (SELECT count(*) FROM route_segment) + (SELECT count(*) FROM segment_facility) <> 0 THEN
        RAISE EXCEPTION '[FAIL] CASCADE 삭제가 동작하지 않음';
    END IF;
    RAISE NOTICE '[OK]   route 삭제 시 route_point / route_segment / segment_facility CASCADE';
END $$;

ROLLBACK;
