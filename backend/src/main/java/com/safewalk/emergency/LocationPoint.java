package com.safewalk.emergency;

import java.time.OffsetDateTime;

/**
 * 위치 한 점. 보호자에게 SSE·API로 그대로 내려가는 형태다.
 *
 * - accuracy: 오차 반경(m). 폰이 알려 주지 않으면 null
 * - recordedAt: 폰에서 위치를 측정한 시각 (서버가 받은 시각이 아님)
 */
public record LocationPoint(double lat, double lng, Double accuracy, OffsetDateTime recordedAt) {
}
