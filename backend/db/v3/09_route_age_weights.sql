-- Run before deploying age-specific route scoring. Safe to run again.
BEGIN;

-- Existing users remain unknown until they supply their date of birth.
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE route ADD COLUMN IF NOT EXISTS safety_age_band VARCHAR(20);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'users'::regclass AND conname = 'ck_users_birth_date') THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_birth_date
            CHECK (birth_date IS NULL OR birth_date BETWEEN DATE '1900-01-01'
                AND (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::DATE);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'route'::regclass AND conname = 'ck_route_safety_age_band') THEN
        ALTER TABLE route ADD CONSTRAINT ck_route_safety_age_band
            CHECK (safety_age_band IN ('UNDER_14', 'AGE_14_PLUS'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS route_facility_weight (
    age_band VARCHAR(20) NOT NULL,
    facility_type VARCHAR(30) NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    CONSTRAINT pk_route_facility_weight PRIMARY KEY (age_band, facility_type),
    CONSTRAINT ck_rfw_age_band CHECK (age_band IN ('UNDER_14', 'AGE_14_PLUS')),
    CONSTRAINT ck_rfw_facility_type CHECK (facility_type IN
        ('CCTV', 'STREET_LIGHT', 'POLICE', 'EMERGENCY_BELL', 'CONVENIENCE_STORE', 'FIRE_STATION')),
    CONSTRAINT ck_rfw_weight CHECK (weight >= 0 AND weight < 'Infinity'::DOUBLE PRECISION)
);
COMMENT ON TABLE route_facility_weight IS '경로 계산용 연령대별 시설 보너스. 회원 유형 ADULT/MINOR와 독립';
COMMENT ON COLUMN users.birth_date IS '경로 연령대 계산용. 기존 계정은 입력 전 NULL';
COMMENT ON COLUMN route.safety_age_band IS '경로 계산 당시 연령대 스냅샷. 이전 앱/기존 경로는 NULL';

-- Initial product settings, to be tuned against route results; not measured safety probabilities.
-- AGE_14_PLUS preserves the previous app bonuses. Do not overwrite team adjustments on rerun.
INSERT INTO route_facility_weight (age_band, facility_type, weight) VALUES
    ('UNDER_14', 'CCTV', 0.20),
    ('UNDER_14', 'STREET_LIGHT', 0.20),
    ('UNDER_14', 'POLICE', 0.30),
    ('UNDER_14', 'EMERGENCY_BELL', 0.25),
    ('UNDER_14', 'CONVENIENCE_STORE', 0.15),
    ('UNDER_14', 'FIRE_STATION', 0.20),
    ('AGE_14_PLUS', 'CCTV', 0.15),
    ('AGE_14_PLUS', 'STREET_LIGHT', 0.10),
    ('AGE_14_PLUS', 'POLICE', 0.15),
    ('AGE_14_PLUS', 'EMERGENCY_BELL', 0.15),
    ('AGE_14_PLUS', 'CONVENIENCE_STORE', 0.15),
    ('AGE_14_PLUS', 'FIRE_STATION', 0.15)
ON CONFLICT (age_band, facility_type) DO NOTHING;
ALTER TABLE route_facility_weight ENABLE ROW LEVEL SECURITY;

COMMIT;
