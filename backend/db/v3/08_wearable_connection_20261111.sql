-- =====================================================================
-- GODGILL DB v3 — 2026-11-11 변경분 (Supabase에 적용 필요)
--
-- 워치 ↔ 계정 연결 규칙 (claude/wearable-connection-v1.md)
--   - is_active = 지금 앱과 연결 중인 기기. 사용자당 1대(기존 uq_wearable_one_active),
--     워치 1대당 1계정(이번에 추가하는 uq_wearable_identifier_connected)
--   - 같은 워치를 여러 계정에 "등록"하는 것은 허용 (기존 uq_wearable_identifier_active 그대로)
--   - last_connected_at = 마지막 연결 상태 변경(연결·해제·재시작 확인) 시각
--   - device_identifier = 워치 ANDROID_ID (워치 앱 설치 UUID에서 변경)
--
-- 빈 DB(01로 생성)에는 실행하지 않음 — 이미 포함되어 있음.
-- 여러 번 실행해도 안전 (IF NOT EXISTS).
-- =====================================================================

BEGIN;

-- 인덱스를 만들기 전에, 같은 워치가 여러 계정에서 연결 중으로 남아 있으면
-- 가장 최근에 연결 상태가 바뀐 1개만 남기고 해제한다 (정상 데이터라면 바뀌는 행 없음)
UPDATE wearable_device w
SET is_active = FALSE,
    last_connected_at = now()
WHERE w.is_active
  AND EXISTS (
      SELECT 1 FROM wearable_device o
      WHERE o.is_active
        AND o.device_identifier = w.device_identifier
        AND o.wearable_id <> w.wearable_id
        AND (COALESCE(o.last_connected_at, o.connected_at), o.wearable_id)
          > (COALESCE(w.last_connected_at, w.connected_at), w.wearable_id));

CREATE UNIQUE INDEX IF NOT EXISTS uq_wearable_identifier_connected
    ON wearable_device (device_identifier) WHERE is_active;

COMMENT ON TABLE wearable_device IS
    '등록 웨어러블. device_identifier = 워치 ANDROID_ID(WEAR_OS) 또는 ESP32 MAC(CUSTOM). 같은 워치를 여러 계정에 등록 가능, 연결은 한 계정만. soft delete';
COMMENT ON COLUMN wearable_device.is_active IS
    '현재 앱과 연결 중인 기기 (사용자당 1대, 워치 1대당 1계정)';
COMMENT ON COLUMN wearable_device.connected_at IS '처음 등록한 시각';
COMMENT ON COLUMN wearable_device.last_connected_at IS
    '마지막 연결 상태 변경 시각 (연결·해제·앱 재시작 확인). 다른 계정의 연결 만료 판단에 사용';

COMMIT;
