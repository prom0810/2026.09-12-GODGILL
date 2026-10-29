-- =====================================================================
-- GODGILL(안심동행) DB — Supabase v1 → v3 마이그레이션
-- 기준: 2026-10-15 ERD 3차 확정본
--
-- 현재 Supabase 상태 (application.yml 기준): v1 테이블 10개
--   user_type, users, guardian                              → 데이터 있음, ALTER로 변경
--   safety_facility_type, facility_type_weight              → 구조 거의 동일, ALTER로 변경
--   route, route_point, route_segment,
--   safety_facility, segment_facility                       → 구조가 크게 바뀜, 삭제 후 재생성
-- 새로 만드는 테이블 10개
--   user_device, notification, wearable_device, risk_area, risk_report,
--   risk_report_image, emergency_event, emergency_event_log,
--   heart_rate_record, motion_record
--
-- 안전장치
--   - 전체가 한 트랜잭션입니다. 중간에 하나라도 실패하면 아무것도 바뀌지 않습니다.
--   - 삭제 후 재생성할 5개 테이블에 데이터가 1행이라도 있으면 즉시 중단합니다.
--   - 새로 만들 10개 테이블 중 이미 존재하는 것이 있으면 즉시 중단합니다.
--
-- 실행 순서: 이 파일 → 03_seed_base.sql → (선택) 04_enable_rls.sql
-- 실행 전 Supabase 대시보드에서 백업(Database → Backups)을 확인하세요.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 0. 사전 점검
-- ---------------------------------------------------------------------

DO $$
DECLARE
    t     TEXT;
    n     BIGINT;
    found TEXT := '';
BEGIN
    -- 삭제 후 재생성할 테이블에 데이터가 있으면 중단
    FOREACH t IN ARRAY ARRAY['route', 'route_point', 'route_segment', 'safety_facility', 'segment_facility'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM public.%I', t) INTO n;
            IF n > 0 THEN
                found := found || format(' %s(%s행)', t, n);
            END IF;
        END IF;
    END LOOP;
    IF found <> '' THEN
        RAISE EXCEPTION '재생성 대상 테이블에 데이터가 있습니다:%. 백업 후 비우고 다시 실행하세요.', found;
    END IF;

    -- 새로 만들 테이블이 이미 있으면 중단
    FOREACH t IN ARRAY ARRAY['user_device', 'notification', 'wearable_device', 'risk_area', 'risk_report',
                             'risk_report_image', 'emergency_event', 'emergency_event_log',
                             'heart_rate_record', 'motion_record'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            found := found || ' ' || t;
        END IF;
    END LOOP;
    IF found <> '' THEN
        RAISE EXCEPTION '새로 만들 테이블이 이미 존재합니다:%. 구조를 확인한 뒤 실행하세요.', found;
    END IF;

    -- 기존 데이터가 새 제약을 어기지 않는지 확인
    IF EXISTS (SELECT 1 FROM users WHERE length(name) > 50 OR length(phone) > 20) THEN
        RAISE EXCEPTION 'users에 name 50자 또는 phone 20자를 넘는 행이 있습니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM guardian WHERE length(name) > 50 OR length(phone) > 20) THEN
        RAISE EXCEPTION 'guardian에 name 50자 또는 phone 20자를 넘는 행이 있습니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM guardian WHERE relationship NOT IN ('PARENT', 'FAMILY', 'ACQUAINTANCE')) THEN
        RAISE EXCEPTION 'guardian.relationship에 PARENT/FAMILY/ACQUAINTANCE 외의 값이 있습니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM guardian WHERE deleted_at IS NULL GROUP BY user_id, phone HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'guardian에 같은 사용자·같은 번호로 삭제되지 않은 보호자가 2명 이상 있습니다.';
    END IF;
END $$;

-- 단일 컬럼 UNIQUE가 이미 있는지 확인하는 임시 함수 (세션 종료 시 사라짐)
CREATE FUNCTION pg_temp.has_unique(tbl TEXT, col TEXT) RETURNS BOOLEAN
LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class c     ON c.oid = i.indrelid
        JOIN pg_namespace s ON s.oid = c.relnamespace AND s.nspname = 'public'
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0]
        WHERE c.relname = tbl AND i.indisunique AND i.indnatts = 1
          AND i.indpred IS NULL AND a.attname = col
    );
$$;

-- ---------------------------------------------------------------------
-- 1. user_type (ALTER)
-- ---------------------------------------------------------------------

ALTER TABLE user_type ALTER COLUMN type_name TYPE VARCHAR(30);
DO $$ BEGIN
    IF NOT pg_temp.has_unique('user_type', 'type_name') THEN
        ALTER TABLE user_type ADD CONSTRAINT uq_user_type_name UNIQUE (type_name);
    END IF;
END $$;
COMMENT ON TABLE user_type IS '사용자 유형 (만 19세 기준 ADULT / MINOR)';

-- ---------------------------------------------------------------------
-- 2. users (ALTER)
-- ---------------------------------------------------------------------

ALTER TABLE users
    ALTER COLUMN name  TYPE VARCHAR(50),
    ALTER COLUMN email TYPE VARCHAR(255),
    ALTER COLUMN phone TYPE VARCHAR(20),
    ALTER COLUMN created_at SET DEFAULT now(),
    ADD COLUMN identity_verified    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN identity_verified_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_users_identity_verified
        CHECK (identity_verified = (identity_verified_at IS NOT NULL));
DO $$ BEGIN
    IF NOT pg_temp.has_unique('users', 'email') THEN
        ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
    END IF;
    IF NOT pg_temp.has_unique('users', 'phone') THEN
        ALTER TABLE users ADD CONSTRAINT uq_users_phone UNIQUE (phone);
    END IF;
END $$;
COMMENT ON TABLE users IS '회원 계정. 탈퇴는 soft delete(deleted_at)';

-- ---------------------------------------------------------------------
-- 3. guardian (ALTER)
-- ---------------------------------------------------------------------

ALTER TABLE guardian
    ALTER COLUMN name         TYPE VARCHAR(50),
    ALTER COLUMN phone        TYPE VARCHAR(20),
    ALTER COLUMN relationship TYPE VARCHAR(30),
    ALTER COLUMN created_at SET DEFAULT now(),
    ADD COLUMN guardian_user_id    BIGINT      NULL,
    ADD COLUMN is_primary          BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'UNREGISTERED',
    ADD COLUMN can_view_location   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN connected_at        TIMESTAMPTZ NULL,
    ADD CONSTRAINT fk_guardian_guardian_user FOREIGN KEY (guardian_user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_guardian_relationship
        CHECK (relationship IN ('PARENT', 'FAMILY', 'ACQUAINTANCE')),
    ADD CONSTRAINT ck_guardian_registration_status
        CHECK (registration_status IN ('UNREGISTERED', 'INVITED', 'CONNECTED', 'REVOKED')),
    ADD CONSTRAINT ck_guardian_not_self
        CHECK (guardian_user_id IS NULL OR guardian_user_id <> user_id),
    ADD CONSTRAINT ck_guardian_connected
        CHECK (registration_status <> 'CONNECTED'
               OR (guardian_user_id IS NOT NULL AND connected_at IS NOT NULL));
CREATE UNIQUE INDEX uq_guardian_user_phone_active
    ON guardian (user_id, phone) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_guardian_primary_active
    ON guardian (user_id) WHERE is_primary AND deleted_at IS NULL;
CREATE INDEX ix_guardian_guardian_user ON guardian (guardian_user_id);
COMMENT ON TABLE guardian IS '보호 대상 사용자와 보호자의 관계. 삭제는 soft delete(deleted_at)';

-- ---------------------------------------------------------------------
-- 4. safety_facility_type, facility_type_weight (없으면 생성, 있으면 ALTER)
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS safety_facility_type (
    facility_type_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    type_name         VARCHAR(30) NOT NULL
);
ALTER TABLE safety_facility_type
    ALTER COLUMN type_name TYPE VARCHAR(30),
    ADD COLUMN IF NOT EXISTS description VARCHAR(255) NULL;
ALTER TABLE safety_facility_type ALTER COLUMN description TYPE VARCHAR(255);
DO $$ BEGIN
    IF NOT pg_temp.has_unique('safety_facility_type', 'type_name') THEN
        ALTER TABLE safety_facility_type ADD CONSTRAINT uq_safety_facility_type_name UNIQUE (type_name);
    END IF;
END $$;
COMMENT ON TABLE safety_facility_type IS '안전시설 유형 코드 (CCTV, EMERGENCY_BELL, POLICE, STREET_LIGHT). type_name이 SEGMENT_FACILITY에 값으로 복사됨';

CREATE TABLE IF NOT EXISTS facility_type_weight (
    facility_type_id  BIGINT           NOT NULL REFERENCES safety_facility_type (facility_type_id) ON DELETE RESTRICT,
    user_type_id      BIGINT           NOT NULL REFERENCES user_type (user_type_id) ON DELETE RESTRICT,
    weight            DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (facility_type_id, user_type_id)
);
ALTER TABLE facility_type_weight
    ALTER COLUMN weight TYPE DOUBLE PRECISION,
    ADD CONSTRAINT ck_ftw_weight CHECK (weight >= 0);
CREATE INDEX IF NOT EXISTS ix_ftw_user_type ON facility_type_weight (user_type_id);
COMMENT ON TABLE facility_type_weight IS '사용자 유형별 시설 가중치';

-- ---------------------------------------------------------------------
-- 5. 구조가 바뀐 v1 테이블 삭제 (0단계에서 비어 있음을 확인함)
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS segment_facility CASCADE;
DROP TABLE IF EXISTS route_segment    CASCADE;
DROP TABLE IF EXISTS route_point      CASCADE;
DROP TABLE IF EXISTS route            CASCADE;
DROP TABLE IF EXISTS safety_facility  CASCADE;

-- ---------------------------------------------------------------------
-- 6. 테이블 생성 (01_full_schema.sql과 동일한 정의)
-- ---------------------------------------------------------------------

CREATE TABLE user_device (
    user_device_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    fcm_token       VARCHAR(512) NOT NULL,
    device_type     VARCHAR(30)  NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_user_device_fcm_token UNIQUE (fcm_token),
    CONSTRAINT fk_user_device_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT
);
COMMENT ON TABLE user_device IS 'FCM 푸시를 받는 앱 설치 기기 (ANDROID, WEAR_OS 등)';
CREATE INDEX ix_user_device_user ON user_device (user_id);

CREATE TABLE notification (
    notification_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    title            VARCHAR(100) NOT NULL,
    content          TEXT         NOT NULL,
    reference_type   VARCHAR(30)  NULL,
    reference_id     BIGINT       NULL,
    read_at          TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_type
        CHECK (type IN ('EMERGENCY', 'RISK_AREA', 'GUARDIAN')),
    CONSTRAINT ck_notification_reference_type
        CHECK (reference_type IN ('RISK_REPORT', 'ROUTE', 'GUARDIAN', 'EMERGENCY_EVENT')),
    CONSTRAINT ck_notification_reference_pair
        CHECK ((reference_type IS NULL) = (reference_id IS NULL))
);
COMMENT ON TABLE notification IS '앱 내 알림 이력. reference_id는 FK가 아닌 다형성 참조(백엔드가 검증)';
CREATE INDEX ix_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX ix_notification_user_unread ON notification (user_id) WHERE read_at IS NULL;

CREATE TABLE wearable_device (
    wearable_id        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    device_name        VARCHAR(100) NULL,
    device_type        VARCHAR(50)  NOT NULL,
    device_identifier  VARCHAR(100) NOT NULL,
    is_active          BOOLEAN      NOT NULL DEFAULT FALSE,
    connected_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_connected_at  TIMESTAMPTZ  NULL,
    deleted_at         TIMESTAMPTZ  NULL,
    CONSTRAINT fk_wearable_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_wearable_device_type
        CHECK (device_type IN ('WEAR_OS', 'CUSTOM')),
    CONSTRAINT ck_wearable_deleted_inactive
        CHECK (deleted_at IS NULL OR is_active = FALSE)
);
COMMENT ON TABLE wearable_device IS '등록 웨어러블. device_identifier = ANDROID_ID(WEAR_OS) 또는 ESP32 MAC(CUSTOM). soft delete';
CREATE UNIQUE INDEX uq_wearable_identifier_active
    ON wearable_device (user_id, device_identifier) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_wearable_one_active
    ON wearable_device (user_id) WHERE is_active;

CREATE TABLE safety_facility (
    facility_id       BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    facility_type_id  BIGINT           NOT NULL,
    quantity          INTEGER          NOT NULL DEFAULT 1,
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    address           VARCHAR(255)     NULL,
    source            VARCHAR(100)     NOT NULL,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT fk_safety_facility_type FOREIGN KEY (facility_type_id)
        REFERENCES safety_facility_type (facility_type_id) ON DELETE RESTRICT,
    CONSTRAINT ck_safety_facility_quantity CHECK (quantity > 0),
    CONSTRAINT ck_safety_facility_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_safety_facility_lng CHECK (lng BETWEEN -180 AND 180)
);
COMMENT ON TABLE safety_facility IS '공공데이터 시설 원본. 갱신은 source 단위로 DELETE 후 INSERT (참조하는 FK 없음)';
CREATE INDEX ix_safety_facility_type ON safety_facility (facility_type_id);
CREATE INDEX ix_safety_facility_source ON safety_facility (source);
CREATE INDEX ix_safety_facility_lat_lng ON safety_facility (lat, lng);

CREATE TABLE route (
    route_id        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id         BIGINT           NOT NULL,
    user_type_id    BIGINT           NOT NULL,
    total_distance  DOUBLE PRECISION NOT NULL,
    estimated_time  INTEGER          NOT NULL,
    safety_score    DOUBLE PRECISION NOT NULL,
    status          VARCHAR(20)      NOT NULL DEFAULT 'IN_PROGRESS',
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ      NULL,
    CONSTRAINT fk_route_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_route_user_type FOREIGN KEY (user_type_id)
        REFERENCES user_type (user_type_id) ON DELETE RESTRICT,
    CONSTRAINT ck_route_total_distance CHECK (total_distance >= 0),
    CONSTRAINT ck_route_estimated_time CHECK (estimated_time >= 0),
    CONSTRAINT ck_route_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_route_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_route_completed_after CHECK (completed_at IS NULL OR completed_at >= created_at)
);
COMMENT ON TABLE route IS '안내를 시작한 경로만 저장. created_at = 안내 시작 시각, user_type_id = 계산 당시 유형 스냅샷';
COMMENT ON COLUMN route.total_distance IS '단위 m';
CREATE INDEX ix_route_user_created ON route (user_id, created_at DESC);
CREATE INDEX ix_route_user_type ON route (user_type_id);

CREATE TABLE route_point (
    point_id    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    route_id    BIGINT           NOT NULL,
    seq         INTEGER          NOT NULL,
    point_type  VARCHAR(20)      NOT NULL,
    name        VARCHAR(100)     NULL,
    address     VARCHAR(255)     NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_route_point_route FOREIGN KEY (route_id)
        REFERENCES route (route_id) ON DELETE CASCADE,
    CONSTRAINT uq_route_point_seq UNIQUE (route_id, seq),
    CONSTRAINT ck_route_point_seq CHECK (seq >= 0),
    CONSTRAINT ck_route_point_type CHECK (point_type IN ('START', 'WAYPOINT', 'END')),
    CONSTRAINT ck_route_point_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_route_point_lng CHECK (lng BETWEEN -180 AND 180)
);
COMMENT ON TABLE route_point IS '경로 지점. 경로당 START·END 각 1개';
CREATE UNIQUE INDEX uq_route_point_start ON route_point (route_id) WHERE point_type = 'START';
CREATE UNIQUE INDEX uq_route_point_end   ON route_point (route_id) WHERE point_type = 'END';

CREATE TABLE route_segment (
    segment_id      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    start_point_id  BIGINT           NOT NULL,
    end_point_id    BIGINT           NOT NULL,
    distance        DOUBLE PRECISION NOT NULL,
    safety_score    DOUBLE PRECISION NOT NULL,
    risk_score      DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_segment_start_point FOREIGN KEY (start_point_id)
        REFERENCES route_point (point_id) ON DELETE CASCADE,
    CONSTRAINT fk_segment_end_point FOREIGN KEY (end_point_id)
        REFERENCES route_point (point_id) ON DELETE CASCADE,
    CONSTRAINT uq_segment_start_point UNIQUE (start_point_id),
    CONSTRAINT uq_segment_end_point UNIQUE (end_point_id),
    CONSTRAINT ck_segment_points_differ CHECK (start_point_id <> end_point_id),
    CONSTRAINT ck_segment_distance CHECK (distance >= 0)
);
COMMENT ON TABLE route_segment IS '경로 구간. 두 지점이 같은 route에 속하는지는 백엔드가 검증';

CREATE TABLE segment_facility (
    segment_facility_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    segment_id           BIGINT           NOT NULL,
    facility_type        VARCHAR(30)      NOT NULL,
    lat                  DOUBLE PRECISION NOT NULL,
    lng                  DOUBLE PRECISION NOT NULL,
    quantity             INTEGER          NOT NULL DEFAULT 1,
    distance             DOUBLE PRECISION NOT NULL,
    applied_score        DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_segment_facility_segment FOREIGN KEY (segment_id)
        REFERENCES route_segment (segment_id) ON DELETE CASCADE,
    CONSTRAINT ck_segment_facility_quantity CHECK (quantity > 0),
    CONSTRAINT ck_segment_facility_distance CHECK (distance >= 0)
);
COMMENT ON TABLE segment_facility IS '추천 당시 구간 주변 시설의 값 스냅샷. SAFETY_FACILITY를 FK로 참조하지 않음';
CREATE INDEX ix_segment_facility_segment ON segment_facility (segment_id);

CREATE TABLE risk_area (
    risk_area_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    risk_type     VARCHAR(50)      NOT NULL,
    source        VARCHAR(20)      NOT NULL DEFAULT 'USER_REPORT',
    lat           DOUBLE PRECISION NOT NULL,
    lng           DOUBLE PRECISION NOT NULL,
    radius        DOUBLE PRECISION NOT NULL,
    description   VARCHAR(500)     NULL,
    risk_score    DOUBLE PRECISION NOT NULL DEFAULT 0,
    status        VARCHAR(20)      NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ      NULL,
    CONSTRAINT ck_risk_area_type
        CHECK (risk_type IN ('DARK_ROAD', 'DAMAGED_SIDEWALK', 'CONSTRUCTION', 'ILLEGAL_PARKING', 'OTHER')),
    CONSTRAINT ck_risk_area_source CHECK (source IN ('USER_REPORT', 'PUBLIC_DATA')),
    CONSTRAINT ck_risk_area_status CHECK (status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT ck_risk_area_radius CHECK (radius > 0),
    CONSTRAINT ck_risk_area_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_risk_area_lng CHECK (lng BETWEEN -180 AND 180)
);
COMMENT ON TABLE risk_area IS '경로 계산에 쓰는 위험구역. 해결 시 삭제하지 않고 status=RESOLVED';
COMMENT ON COLUMN risk_area.radius IS '단위 m';
CREATE INDEX ix_risk_area_status ON risk_area (status);
CREATE INDEX ix_risk_area_lat_lng ON risk_area (lat, lng);

CREATE TABLE risk_report (
    report_id     BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id       BIGINT           NOT NULL,
    risk_type     VARCHAR(50)      NOT NULL,
    lat           DOUBLE PRECISION NOT NULL,
    lng           DOUBLE PRECISION NOT NULL,
    address       VARCHAR(255)     NULL,
    description   VARCHAR(500)     NULL,
    is_anonymous  BOOLEAN          NOT NULL DEFAULT FALSE,
    status        VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
    risk_area_id  BIGINT           NULL,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ      NULL,
    CONSTRAINT fk_risk_report_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_risk_report_area FOREIGN KEY (risk_area_id)
        REFERENCES risk_area (risk_area_id) ON DELETE RESTRICT,
    CONSTRAINT ck_risk_report_type
        CHECK (risk_type IN ('DARK_ROAD', 'DAMAGED_SIDEWALK', 'CONSTRUCTION', 'ILLEGAL_PARKING', 'OTHER')),
    CONSTRAINT ck_risk_report_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_risk_report_area_when_approved
        CHECK ((status = 'APPROVED') = (risk_area_id IS NOT NULL)),
    CONSTRAINT ck_risk_report_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_risk_report_lng CHECK (lng BETWEEN -180 AND 180)
);
COMMENT ON TABLE risk_report IS '사용자 제보 원본. 승인 시 status와 risk_area_id를 같은 UPDATE로 변경';
CREATE INDEX ix_risk_report_status ON risk_report (status);
CREATE INDEX ix_risk_report_area ON risk_report (risk_area_id);
CREATE INDEX ix_risk_report_user ON risk_report (user_id);

CREATE TABLE risk_report_image (
    image_id      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    report_id     BIGINT       NOT NULL,
    storage_path  VARCHAR(500) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_risk_report_image_report FOREIGN KEY (report_id)
        REFERENCES risk_report (report_id) ON DELETE CASCADE,
    CONSTRAINT uq_risk_report_image_path UNIQUE (storage_path)
);
COMMENT ON TABLE risk_report_image IS '제보 사진 메타데이터. 파일은 Supabase Storage';
CREATE INDEX ix_risk_report_image_report ON risk_report_image (report_id);

CREATE TABLE emergency_event (
    emergency_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id       BIGINT           NOT NULL,
    wearable_id   BIGINT           NULL,
    route_id      BIGINT           NULL,
    segment_id    BIGINT           NULL,
    type          VARCHAR(30)      NOT NULL,
    source        VARCHAR(30)      NOT NULL,
    status        VARCHAR(20)      NOT NULL DEFAULT 'OCCURRED',
    lat           DOUBLE PRECISION NULL,
    lng           DOUBLE PRECISION NULL,
    occurred_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ      NULL,
    CONSTRAINT fk_emergency_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_emergency_wearable FOREIGN KEY (wearable_id)
        REFERENCES wearable_device (wearable_id) ON DELETE RESTRICT,
    CONSTRAINT fk_emergency_route FOREIGN KEY (route_id)
        REFERENCES route (route_id) ON DELETE RESTRICT,
    CONSTRAINT fk_emergency_segment FOREIGN KEY (segment_id)
        REFERENCES route_segment (segment_id) ON DELETE RESTRICT,
    CONSTRAINT ck_emergency_type CHECK (type IN ('SOS', 'FALL', 'ABNORMAL_HEART_RATE')),
    CONSTRAINT ck_emergency_source CHECK (source IN ('MOBILE', 'WEARABLE', 'SYSTEM')),
    CONSTRAINT ck_emergency_status
        CHECK (status IN ('OCCURRED', 'IN_PROGRESS', 'RESOLVED', 'FALSE_ALARM', 'CANCELLED')),
    CONSTRAINT ck_emergency_auto_detect_context
        CHECK (type = 'SOS' OR (wearable_id IS NOT NULL AND segment_id IS NOT NULL)),
    CONSTRAINT ck_emergency_resolved_when_closed
        CHECK ((status IN ('OCCURRED', 'IN_PROGRESS')) = (resolved_at IS NULL)),
    CONSTRAINT ck_emergency_resolved_after
        CHECK (resolved_at IS NULL OR resolved_at >= occurred_at),
    CONSTRAINT ck_emergency_lat_lng_pair CHECK ((lat IS NULL) = (lng IS NULL)),
    CONSTRAINT ck_emergency_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_emergency_lng CHECK (lng BETWEEN -180 AND 180)
);
COMMENT ON TABLE emergency_event IS 'SOS 및 센서 자동 감지 긴급 사건. lat/lng는 발생 당시 위치 스냅샷(GPS 실패 시 NULL)';
CREATE UNIQUE INDEX uq_emergency_one_open
    ON emergency_event (user_id) WHERE status IN ('OCCURRED', 'IN_PROGRESS');
CREATE INDEX ix_emergency_user_occurred ON emergency_event (user_id, occurred_at DESC);
CREATE INDEX ix_emergency_wearable ON emergency_event (wearable_id);
CREATE INDEX ix_emergency_route ON emergency_event (route_id);
CREATE INDEX ix_emergency_segment ON emergency_event (segment_id);

CREATE TABLE emergency_event_log (
    event_log_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    emergency_id  BIGINT       NOT NULL,
    event_type    VARCHAR(30)  NOT NULL,
    message       VARCHAR(500) NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_emergency_log_event FOREIGN KEY (emergency_id)
        REFERENCES emergency_event (emergency_id) ON DELETE CASCADE
);
COMMENT ON TABLE emergency_event_log IS '긴급 사건 처리 이력 (CREATED, GUARDIAN_NOTIFIED, LOCATION_UPDATED, GUARDIAN_CONFIRMED, RESOLVED 등)';
CREATE INDEX ix_emergency_log_event ON emergency_event_log (emergency_id, created_at);

CREATE TABLE heart_rate_record (
    heart_rate_id   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    wearable_id     BIGINT           NOT NULL,
    user_id         BIGINT           NOT NULL,
    segment_id      BIGINT           NOT NULL,
    start_distance  DOUBLE PRECISION NOT NULL,
    end_distance    DOUBLE PRECISION NOT NULL,
    avg_bpm         SMALLINT         NOT NULL,
    min_bpm         SMALLINT         NOT NULL,
    max_bpm         SMALLINT         NOT NULL,
    sample_count    INTEGER          NOT NULL,
    started_at      TIMESTAMPTZ      NOT NULL,
    ended_at        TIMESTAMPTZ      NOT NULL,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT fk_heart_rate_wearable FOREIGN KEY (wearable_id)
        REFERENCES wearable_device (wearable_id) ON DELETE RESTRICT,
    CONSTRAINT fk_heart_rate_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_heart_rate_segment FOREIGN KEY (segment_id)
        REFERENCES route_segment (segment_id) ON DELETE RESTRICT,
    CONSTRAINT ck_heart_rate_bpm_order CHECK (min_bpm > 0 AND min_bpm <= avg_bpm AND avg_bpm <= max_bpm),
    CONSTRAINT ck_heart_rate_distance CHECK (start_distance >= 0 AND end_distance >= start_distance),
    CONSTRAINT ck_heart_rate_sample_count CHECK (sample_count > 0),
    CONSTRAINT ck_heart_rate_time CHECK (ended_at >= started_at)
);
COMMENT ON TABLE heart_rate_record IS '구간 이동 중 심박 집계 (100m / 2분 / 구간 종료 중 먼저 오는 시점마다 1행)';
CREATE INDEX ix_heart_rate_segment ON heart_rate_record (segment_id);
CREATE INDEX ix_heart_rate_user_started ON heart_rate_record (user_id, started_at);
CREATE INDEX ix_heart_rate_wearable ON heart_rate_record (wearable_id);

CREATE TABLE motion_record (
    motion_id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id           BIGINT           NOT NULL,
    wearable_id       BIGINT           NOT NULL,
    segment_id        BIGINT           NOT NULL,
    start_distance    DOUBLE PRECISION NOT NULL,
    end_distance      DOUBLE PRECISION NOT NULL,
    event_type        VARCHAR(30)      NOT NULL DEFAULT 'NORMAL',
    avg_acceleration  DOUBLE PRECISION NOT NULL,
    max_acceleration  DOUBLE PRECISION NOT NULL,
    avg_gyro          DOUBLE PRECISION NOT NULL,
    max_gyro          DOUBLE PRECISION NOT NULL,
    sample_count      INTEGER          NOT NULL,
    started_at        TIMESTAMPTZ      NOT NULL,
    ended_at          TIMESTAMPTZ      NOT NULL,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT fk_motion_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_motion_wearable FOREIGN KEY (wearable_id)
        REFERENCES wearable_device (wearable_id) ON DELETE RESTRICT,
    CONSTRAINT fk_motion_segment FOREIGN KEY (segment_id)
        REFERENCES route_segment (segment_id) ON DELETE RESTRICT,
    CONSTRAINT ck_motion_event_type CHECK (event_type IN ('NORMAL', 'FALL_SUSPECTED', 'SUDDEN_IMPACT')),
    CONSTRAINT ck_motion_acceleration CHECK (avg_acceleration >= 0 AND avg_acceleration <= max_acceleration),
    CONSTRAINT ck_motion_gyro CHECK (avg_gyro >= 0 AND avg_gyro <= max_gyro),
    CONSTRAINT ck_motion_distance CHECK (start_distance >= 0 AND end_distance >= start_distance),
    CONSTRAINT ck_motion_sample_count CHECK (sample_count > 0),
    CONSTRAINT ck_motion_time CHECK (ended_at >= started_at)
);
COMMENT ON TABLE motion_record IS '구간 이동 중 가속도·자이로 집계. 원시 샘플은 저장하지 않음';
CREATE INDEX ix_motion_segment ON motion_record (segment_id);
CREATE INDEX ix_motion_user_started ON motion_record (user_id, started_at);
CREATE INDEX ix_motion_wearable ON motion_record (wearable_id);

COMMIT;
