-- Run on a test DB after 01 + 03, or 09 + 10 + 11. Assumes unmodified defaults.
BEGIN;
DO $$ BEGIN
    IF (SELECT count(*) FROM route_facility_weight) <> 12 THEN
        RAISE EXCEPTION 'Expected two bands with six weights each';
    END IF;
    IF EXISTS (SELECT 1 FROM route_facility_weight WHERE weight < 0 OR weight > 0.10) THEN
        RAISE EXCEPTION 'Invalid individual support budget';
    END IF;
    IF EXISTS (SELECT 1 FROM route_facility_weight GROUP BY age_band HAVING abs(sum(weight) - 0.10) > 1e-9) THEN
        RAISE EXCEPTION 'Default total support must be 0.10 for each band';
    END IF;
    IF (SELECT jsonb_object_agg(facility_type, weight) FROM route_facility_weight WHERE age_band = 'UNDER_14')
       IS DISTINCT FROM
       (SELECT jsonb_object_agg(facility_type, weight) FROM route_facility_weight WHERE age_band = 'AGE_14_PLUS') THEN
        RAISE EXCEPTION 'Unvalidated age multipliers must not be enabled';
    END IF;
    IF EXISTS (SELECT 1 FROM route_facility_weight WHERE
        (facility_type = 'CCTV' AND weight <> 0.02) OR
        (facility_type IN ('STREET_LIGHT', 'POLICE') AND weight <> 0.04) OR
        (facility_type NOT IN ('CCTV', 'STREET_LIGHT', 'POLICE') AND weight <> 0.0)) THEN
        RAISE EXCEPTION 'Expected conservative common defaults';
    END IF;
END $$;
ROLLBACK;
