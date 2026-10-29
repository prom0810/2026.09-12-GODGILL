-- =====================================================================
-- 06. 기존 보호자 데이터에 대표 보호자 지정 (1회 실행)
-- =====================================================================
-- 배경
--   v1 → v3 마이그레이션(02)에서 guardian.is_primary가 기본값 FALSE로 추가돼,
--   기존 사용자들은 대표 보호자가 한 명도 없는 상태다.
--   백엔드는 "보호자가 1명 이상이면 대표는 정확히 1명"을 전제로 동작하므로
--   (대표 승계·대표 해제 금지 규칙), 사용자별로 가장 먼저 등록된 보호자를 대표로 지정한다.
--
-- 실행
--   Supabase SQL Editor에서 02 → 03 이후 한 번 실행. 여러 번 실행해도 결과는 같다
--   (이미 대표가 있는 사용자는 건너뛴다).
-- =====================================================================

BEGIN;

UPDATE guardian g
SET is_primary = TRUE
WHERE g.guardian_id IN (
    SELECT DISTINCT ON (user_id) guardian_id
    FROM guardian
    WHERE deleted_at IS NULL
    ORDER BY user_id, guardian_id
)
AND NOT EXISTS (
    SELECT 1 FROM guardian p
    WHERE p.user_id = g.user_id AND p.is_primary AND p.deleted_at IS NULL
);

-- 확인: 보호자가 있는데 대표가 없는 사용자 수 (0이어야 함)
SELECT count(*) AS users_without_primary
FROM (
    SELECT user_id
    FROM guardian
    WHERE deleted_at IS NULL
    GROUP BY user_id
    HAVING bool_or(is_primary) = FALSE
) t;

COMMIT;
