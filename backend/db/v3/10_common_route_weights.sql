-- Apply after 09. Installs the common, bounded proximity policy once.
-- Previous values are retained in route_weight_policy_history for review/rollback.
BEGIN;

CREATE TABLE IF NOT EXISTS route_weight_policy_history (
    policy_id VARCHAR(50) PRIMARY KEY,
    previous_weights JSONB NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE route_weight_policy_history ENABLE ROW LEVEL SECURITY;
LOCK TABLE route_facility_weight IN SHARE ROW EXCLUSIVE MODE;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM route_weight_policy_history WHERE policy_id = 'COMMON_PROXIMITY_V2') THEN
        INSERT INTO route_weight_policy_history (policy_id, previous_weights)
        SELECT 'COMMON_PROXIMITY_V2', COALESCE(jsonb_agg(to_jsonb(w) ORDER BY age_band, facility_type), '[]'::jsonb)
        FROM route_facility_weight w;

        INSERT INTO route_facility_weight (age_band, facility_type, weight)
        SELECT a.age_band, f.facility_type, f.weight
        FROM (VALUES ('UNDER_14'), ('AGE_14_PLUS')) a(age_band)
        CROSS JOIN (VALUES
            ('CCTV', 0.04::DOUBLE PRECISION), ('STREET_LIGHT', 0.06::DOUBLE PRECISION),
            ('POLICE', 0.0::DOUBLE PRECISION), ('EMERGENCY_BELL', 0.0::DOUBLE PRECISION),
            ('CONVENIENCE_STORE', 0.0::DOUBLE PRECISION), ('FIRE_STATION', 0.0::DOUBLE PRECISION)
        ) f(facility_type, weight)
        ON CONFLICT (age_band, facility_type) DO UPDATE SET weight = EXCLUDED.weight;
    END IF;
END $$;

COMMENT ON TABLE route_facility_weight IS '연령 구분과 독립인 공통 근접성 보너스. 합계 0.10 이하를 서버에서도 검증';
COMMIT;
