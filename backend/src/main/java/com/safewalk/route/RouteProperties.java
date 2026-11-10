package com.safewalk.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 경로 보관·정리 설정 (application.yml의 route.*, .env로 바꿀 수 있다).
 *
 *  - staleHours: 진행 중(IN_PROGRESS)으로 이 시간이 지나면 자동 취소 (기본 6시간)
 *  - detailRetentionDays: 이 기간이 지난 경로는 경유 지점·구간·시설을 지우고 요약(경로 + 출발·도착)만 남긴다 (기본 65일)
 *  - deleteAfterDays: 이 기간이 지난 경로는 완전히 삭제한다 (기본 365일)
 *  - batchSize: 정리할 때 한 번에 처리하는 경로 수
 */
@Component
public class RouteProperties {

    private final long staleHours;
    private final long detailRetentionDays;
    private final long deleteAfterDays;
    private final int batchSize;

    public RouteProperties(
            @Value("${route.stale-in-progress-hours:6}") long staleHours,
            @Value("${route.detail-retention-days:65}") long detailRetentionDays,
            @Value("${route.delete-after-days:365}") long deleteAfterDays,
            @Value("${route.cleanup-batch-size:500}") int batchSize) {
        if (staleHours < 1 || detailRetentionDays < 1 || batchSize < 1) {
            throw new IllegalStateException("route.* 설정값은 1 이상이어야 합니다.");
        }
        if (deleteAfterDays <= detailRetentionDays) {
            throw new IllegalStateException("route.delete-after-days는 route.detail-retention-days보다 커야 합니다.");
        }
        this.staleHours = staleHours;
        this.detailRetentionDays = detailRetentionDays;
        this.deleteAfterDays = deleteAfterDays;
        this.batchSize = batchSize;
    }

    public long staleHours() {
        return staleHours;
    }

    public long detailRetentionDays() {
        return detailRetentionDays;
    }

    public long deleteAfterDays() {
        return deleteAfterDays;
    }

    public int batchSize() {
        return batchSize;
    }
}
