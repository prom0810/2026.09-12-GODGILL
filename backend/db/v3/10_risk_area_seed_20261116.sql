-- 위험구역 목업 시드 (2026-11-16)
--
-- 앱 RouteSafetyScorer에 하드코딩돼 있던 목업 위험구역 4개를 risk_area로 옮긴다.
-- 앱은 이제 GET /api/risk-areas(ACTIVE)로 위험구역을 받아 경로 안전도 감점과 안내 중 접근·진입 알림에 쓴다.
-- 서버에서 받지 못하면(오프라인·첫 실행) 앱에 남겨 둔 같은 목업 4개를 쓴다.
--
-- 좌표: 천안역 기준점(36.8100, 127.1467)에서 북/동 오프셋(m)을 앱과 같은 공식으로 환산한 값
--   (위도 1m = 1/111320도, 경도 1m = 1/(111320·cos 36.81°)도)
-- risk_type: 목업 범죄주의구간이라 맞는 유형이 없어 OTHER. 실제 데이터로 바꿀 때 RESOLVED 처리하거나 삭제한다.
--
-- 여러 번 실행해도 안전 (같은 description이 있으면 건너뜀). Supabase SQL Editor에서 실행.

BEGIN;

INSERT INTO risk_area (risk_type, source, lat, lng, radius, description, risk_score, status)
SELECT v.risk_type, 'PUBLIC_DATA', v.lat, v.lng, v.radius, v.description, v.risk_score, 'ACTIVE'
FROM (VALUES
    ('OTHER', 36.8046101, 127.1500660, 250.0, '[목업] 범죄주의구간 1 (천안역 남동 670m)', 0.70),
    ('OTHER', 36.8162882, 127.1422120, 200.0, '[목업] 범죄주의구간 2 (천안역 북서 810m)', 0.55),
    ('OTHER', 36.8117966, 127.1567981, 180.0, '[목업] 범죄주의구간 3 (천안역 동 920m)', 0.60),
    ('OTHER', 36.8001186, 127.1388459, 220.0, '[목업] 범죄주의구간 4 (천안역 남서 1.3km)', 0.50)
) AS v(risk_type, lat, lng, radius, description, risk_score)
WHERE NOT EXISTS (SELECT 1 FROM risk_area a WHERE a.description = v.description);

COMMIT;

-- 확인: SELECT risk_area_id, description, lat, lng, radius, risk_score, status FROM risk_area ORDER BY risk_area_id;
