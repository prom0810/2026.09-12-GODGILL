package com.safewalk.report;

import java.time.OffsetDateTime;

/**
 * 제보 사진 응답.
 * url은 일정 시간(urlExpiresInSeconds) 뒤 만료되므로 앱에 저장해 두지 말고, 필요할 때 제보를 다시 조회해서 받는다.
 */
public record RiskReportImageResponse(
        Long imageId,
        String url,
        long urlExpiresInSeconds,
        OffsetDateTime createdAt
) {
}
