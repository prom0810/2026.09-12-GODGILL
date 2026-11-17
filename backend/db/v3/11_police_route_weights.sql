-- Apply after 10. Include access to police facilities in the shared support budget.
BEGIN;
LOCK TABLE route_facility_weight IN SHARE ROW EXCLUSIVE MODE;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM route_weight_policy_history WHERE policy_id = 'COMMON_WITH_POLICE_V3') THEN
        INSERT INTO route_weight_policy_history (policy_id, previous_weights)
        SELECT 'COMMON_WITH_POLICE_V3', COALESCE(jsonb_agg(to_jsonb(w) ORDER BY age_band, facility_type), '[]'::jsonb)
        FROM route_facility_weight w;

        INSERT INTO route_facility_weight (age_band, facility_type, weight)
        SELECT a.age_band, f.facility_type, f.weight
        FROM (VALUES ('UNDER_14'), ('AGE_14_PLUS')) a(age_band)
        CROSS JOIN (VALUES
            ('CCTV', 0.02::DOUBLE PRECISION), ('STREET_LIGHT', 0.04::DOUBLE PRECISION),
            ('POLICE', 0.04::DOUBLE PRECISION), ('EMERGENCY_BELL', 0.0::DOUBLE PRECISION),
            ('CONVENIENCE_STORE', 0.0::DOUBLE PRECISION), ('FIRE_STATION', 0.0::DOUBLE PRECISION)
        ) f(facility_type, weight)
        ON CONFLICT (age_band, facility_type) DO UPDATE SET weight = EXCLUDED.weight;
    END IF;
END $$;
COMMIT;
