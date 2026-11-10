package com.safewalk.route;

import java.util.Locale;

/**
 * segment_facility.facility_type 값.
 * 앞의 네 개는 safety_facility_type 코드표와 같은 이름이고, 편의점·소방서는 앱 점수 계산에 쓰여서 추가했다.
 *
 * 앱 데이터와의 대응
 *  - CCTV            ← 천안시 CCTV (CctvSite)
 *  - STREET_LIGHT    ← 보안등 (SecurityLightSite)
 *  - POLICE          ← 카카오 "경찰" 검색 (FacilityType.SECURITY)
 *  - EMERGENCY_BELL  ← (아직 앱에서 쓰지 않음)
 *  - CONVENIENCE_STORE ← 카카오 편의점 (FacilityType.CONVENIENCE_STORE)
 *  - FIRE_STATION    ← 카카오 "소방" 검색 (FacilityType.FIRE)
 */
public enum SegmentFacilityType {
    CCTV,
    STREET_LIGHT,
    POLICE,
    EMERGENCY_BELL,
    CONVENIENCE_STORE,
    FIRE_STATION;

    public static SegmentFacilityType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("시설 종류(type)를 입력해 주세요.");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "시설 종류는 CCTV, STREET_LIGHT, POLICE, EMERGENCY_BELL, CONVENIENCE_STORE, FIRE_STATION 중 하나여야 합니다: " + raw);
        }
    }
}
