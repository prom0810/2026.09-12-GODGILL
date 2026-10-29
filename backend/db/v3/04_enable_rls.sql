-- =====================================================================
-- GODGILL DB v3 — (선택) Supabase 공개 API 차단
--
-- Supabase는 public 스키마의 테이블을 REST API(anon / authenticated 키)로도 노출합니다.
-- RLS가 꺼져 있으면 앱에 들어 있는 anon 키만으로 users(비밀번호 해시 포함) 등을 읽을 수 있습니다.
-- 이 프로젝트는 Spring Boot 백엔드만 DB에 접속하므로, RLS를 켜고 정책을 만들지 않으면
-- REST API 접근은 모두 막히고 백엔드(테이블 소유자 postgres 계정)는 그대로 동작합니다.
--
-- 주의: 백엔드 DB 계정이 테이블 소유자가 아니라면 백엔드 접근도 막힙니다.
--       .env의 DB_USERNAME이 postgres(또는 postgres.<프로젝트ID>)인지 먼저 확인하세요.
-- =====================================================================

BEGIN;

ALTER TABLE user_type            ENABLE ROW LEVEL SECURITY;
ALTER TABLE users                ENABLE ROW LEVEL SECURITY;
ALTER TABLE guardian             ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_device          ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification         ENABLE ROW LEVEL SECURITY;
ALTER TABLE wearable_device      ENABLE ROW LEVEL SECURITY;
ALTER TABLE safety_facility_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE facility_type_weight ENABLE ROW LEVEL SECURITY;
ALTER TABLE safety_facility      ENABLE ROW LEVEL SECURITY;
ALTER TABLE route                ENABLE ROW LEVEL SECURITY;
ALTER TABLE route_point          ENABLE ROW LEVEL SECURITY;
ALTER TABLE route_segment        ENABLE ROW LEVEL SECURITY;
ALTER TABLE segment_facility     ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_area            ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_report          ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_report_image    ENABLE ROW LEVEL SECURITY;
ALTER TABLE emergency_event      ENABLE ROW LEVEL SECURITY;
ALTER TABLE emergency_event_log  ENABLE ROW LEVEL SECURITY;
ALTER TABLE heart_rate_record    ENABLE ROW LEVEL SECURITY;
ALTER TABLE motion_record        ENABLE ROW LEVEL SECURITY;

COMMIT;
