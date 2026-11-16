-- Apply 01 + 03, or 09 + 10 + 11, first. Test data is rolled back.
BEGIN;
DO $$
DECLARE
    test_user BIGINT;
    test_route BIGINT;
    invalid_sql TEXT;
BEGIN
    IF (SELECT count(*) FROM route_facility_weight) <> 12
       OR (SELECT count(DISTINCT age_band) FROM route_facility_weight) <> 2 THEN
        RAISE EXCEPTION 'Expected two age bands with six facility weights each';
    END IF;

    INSERT INTO users (user_type_id, name, email, password, phone, birth_date)
        SELECT user_type_id, 'Age test', 'age-sql-test@example.test', 'hash', '010-0000-0014', DATE '2015-01-01'
        FROM user_type WHERE type_name = 'MINOR'
        RETURNING user_id INTO test_user;
    INSERT INTO route (user_id, user_type_id, total_distance, estimated_time, safety_score, safety_age_band)
        SELECT test_user, user_type_id, 100, 90, 0.5, 'UNDER_14' FROM users WHERE user_id = test_user
        RETURNING route_id INTO test_route;

    UPDATE users SET birth_date = DATE '2000-01-01' WHERE user_id = test_user;
    IF (SELECT safety_age_band FROM route WHERE route_id = test_route) <> 'UNDER_14' THEN
        RAISE EXCEPTION 'Recorded route age band must remain a snapshot';
    END IF;

    FOREACH invalid_sql IN ARRAY ARRAY[
        'UPDATE route_facility_weight SET weight = -0.1 WHERE age_band = ''UNDER_14''',
        'UPDATE route_facility_weight SET weight = ''NaN''::DOUBLE PRECISION WHERE age_band = ''UNDER_14''',
        'UPDATE route_facility_weight SET weight = ''Infinity''::DOUBLE PRECISION WHERE age_band = ''UNDER_14''',
        'UPDATE route_facility_weight SET age_band = ''TEEN'' WHERE age_band = ''UNDER_14''',
        'UPDATE route_facility_weight SET facility_type = ''UNKNOWN'' WHERE facility_type = ''CCTV''',
        format('UPDATE users SET birth_date = CURRENT_DATE + 2 WHERE user_id = %s', test_user),
        format('UPDATE users SET birth_date = DATE ''1899-12-31'' WHERE user_id = %s', test_user),
        format('UPDATE route SET safety_age_band = ''TEEN'' WHERE route_id = %s', test_route)
    ] LOOP
        BEGIN
            EXECUTE invalid_sql;
            RAISE EXCEPTION 'Expected a CHECK violation for %', invalid_sql;
        EXCEPTION WHEN check_violation THEN
            NULL;
        END;
    END LOOP;
END $$;
ROLLBACK;
