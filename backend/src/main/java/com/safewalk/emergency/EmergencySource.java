package com.safewalk.emergency;

/**
 * emergency_event.source (DB ck_emergency_source). SOS가 어디서 시작됐는지.
 *
 * - MOBILE: 폰 앱의 SOS 버튼
 * - WEARABLE: 워치·M5StickC의 SOS 버튼. 워치는 직접 서버로 보내지 않고 폰 앱을 거쳐 보낸다 (GPS·로그인이 폰에 있음)
 * - SYSTEM: 서버가 자동으로 만든 경우 (예정)
 */
public enum EmergencySource {
    MOBILE,
    WEARABLE,
    SYSTEM
}
