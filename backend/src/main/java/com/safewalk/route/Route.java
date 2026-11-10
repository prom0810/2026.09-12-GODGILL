package com.safewalk.route;

import com.safewalk.user.User;
import com.safewalk.user.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 안내를 시작한 경로 1건 (DB v3 route). created_at = 안내 시작 시각.
 *
 * - userType: 안내 시작 당시 사용자 유형 스냅샷 (나중에 유형이 바뀌어도 그대로)
 * - totalDistance(m): 서버가 지점 좌표로 다시 계산한 값
 * - estimatedTime(초): 앱이 계산해 보낸 예상 소요 시간
 * - safetyScore(0~1): 앱 화면에 보여 준 경로 평균 안전점수
 *
 * 저장(INSERT)은 JPA가 아니라 {@link RouteWriter}가 한다. ID를 시퀀스에서 미리 받아 지점·구간을 한 번에
 * 넣기 위해서다. 그래서 @GeneratedValue가 없다. 이 엔티티는 조회와 상태 변경(도착·취소)에만 쓴다.
 *
 * 상태 전이 (맞지 않으면 {@link RouteStateException}, 409)
 *   IN_PROGRESS ──complete──▶ COMPLETED (completed_at 기록)
 *   IN_PROGRESS ──cancel────▶ CANCELLED
 */
@Entity
@Table(name = "route")
public class Route {

    @Id
    @Column(name = "route_id")
    private Long routeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_type_id", nullable = false)
    private UserType userType;

    @Column(name = "total_distance", nullable = false)
    private double totalDistance;

    @Column(name = "estimated_time", nullable = false)
    private int estimatedTime;

    @Column(name = "safety_score", nullable = false)
    private double safetyScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RouteStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /** JPA 전용 기본 생성자 */
    protected Route() {
    }

    public Long getRouteId() {
        return routeId;
    }

    public User getUser() {
        return user;
    }

    public UserType getUserType() {
        return userType;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public int getEstimatedTime() {
        return estimatedTime;
    }

    public double getSafetyScore() {
        return safetyScore;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    /** 도착 처리 */
    public void complete(OffsetDateTime at) {
        requireInProgress();
        this.status = RouteStatus.COMPLETED;
        // DB 제약 ck_route_completed_after: completed_at >= created_at
        this.completedAt = at.isBefore(createdAt) ? createdAt : at;
    }

    /** 안내 취소 (사용자 취소, 새 안내로 대체) */
    public void cancel() {
        requireInProgress();
        this.status = RouteStatus.CANCELLED;
    }

    private void requireInProgress() {
        if (status != RouteStatus.IN_PROGRESS) {
            throw new RouteStateException("이미 끝난 경로 안내입니다. (현재 상태: " + status + ")");
        }
    }

    /** 테스트 전용: DB 없이 상태 전이를 확인할 때 쓴다. */
    static Route forTest(Long routeId, RouteStatus status, OffsetDateTime createdAt) {
        Route route = new Route();
        route.routeId = routeId;
        route.status = status;
        route.createdAt = createdAt;
        return route;
    }
}
