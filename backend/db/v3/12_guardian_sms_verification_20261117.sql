-- =====================================================================
-- GODGILL DB v3 — 2026-11-17 보호자 확인(인증번호) (Supabase에 적용 필요)
--
-- guardian.sms_verified_at 추가
--   앱에 가입하지 않은 보호자가 인증번호로 "SOS 문자 수신"에 동의한 시각. NULL이면 미확인.
--   SOS 시작·종료 문자는 확인된(NULL이 아닌) 대표 보호자에게만 보낸다.
--   기존 보호자는 모두 NULL(미확인)로 시작한다 → 앱에서 인증번호 확인을 다시 받아야 문자가 간다.
--
-- 이 컬럼을 매핑하는 백엔드(feature/backend/guardian-verification)는 ddl-auto=validate라
-- 이 파일을 먼저 실행해야 시작된다. 이전 백엔드는 컬럼이 늘어도 그대로 동작한다.
-- 여러 번 실행해도 안전. 빈 DB(01로 생성)에는 이미 들어 있어 실행할 필요 없음.
--
-- 파일 번호: 열려 있는 PR(#45: 09~11, #46: 10)과 겹치지 않게 12를 사용.
-- =====================================================================

BEGIN;

ALTER TABLE guardian ADD COLUMN IF NOT EXISTS sms_verified_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN guardian.sms_verified_at IS
    '인증번호로 SOS 문자 수신에 동의한 시각. NULL이면 미확인 → SOS 문자 발송 안 함 (2026-11-17)';

COMMIT;
