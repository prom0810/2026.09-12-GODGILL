package com.safewalk.emergency;

import com.safewalk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 긴급 사건 한 건 (DB v3 emergency_event).
 *
 * - lat/lng: 발생 당시 위치 스냅샷. GPS를 못 잡았으면 둘 다 null. 이후 이동 위치는 DB에 저장하지 않는다 (서버 메모리)
 * - wearableId/routeId/segmentId: 아직 해당 엔티티가 없어 값(Long)으로만 매핑한다. 지금 만드는 SOS에서는 모두 null
 * - resolvedBy: 종료한 계정(사용자 본인 또는 보호자). 자동 종료면 null
 *
 * 상태 전이 (맞지 않으면 {@link EmergencyStateException}, 409)
 *   OCCURRED ──resolve──▶ RESOLVED / FALSE_ALARM / CANCELLED
 *   OCCURRED ──autoClose──▶ CANCELLED (resolvedBy 없음)
 */
@Entity
@Table(name = "emergency_event")
public class EmergencyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emergency_id")
    private Long emergencyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "wearable_id")
    private Long wearableId;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "segment_id")
    private Long segmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private EmergencyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private EmergencySource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmergencyStatus status = EmergencyStatus.OCCURRED;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    /** JPA 전용 기본 생성자 */
    protected EmergencyEvent() {
    }

    private EmergencyEvent(User user, EmergencyType type, EmergencySource source, Double lat, Double lng) {
        this.user = user;
        this.type = type;
        this.source = source;
        this.status = EmergencyStatus.OCCURRED;
        this.lat = lat;
        this.lng = lng;
        this.occurredAt = now();
    }

    /**
     * 사용자가 직접 누른 SOS. 좌표는 둘 다 있거나 둘 다 없어야 한다.
     * 좌표 범위·쌍 검사는 서비스에서 400으로 먼저 걸러지고, 여기서는 마지막 방어선이다.
     */
    public static EmergencyEvent sos(User user, EmergencySource source, Double lat, Double lng) {
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("lat과 lng는 함께 보내야 합니다.");
        }
        if (source == EmergencySource.SYSTEM) {
            throw new IllegalArgumentException("SOS의 source는 MOBILE 또는 WEARABLE이어야 합니다.");
        }
        return new EmergencyEvent(user, EmergencyType.SOS, source, lat, lng);
    }

    /** 사용자 또는 보호자가 종료. 누가 어떤 상태로 끝낼 수 있는지는 서비스가 검사한다. */
    public void resolve(EmergencyStatus closedStatus, User by) {
        if (closedStatus == null || closedStatus.isOpen()) {
            throw new IllegalArgumentException("종료 상태는 RESOLVED, FALSE_ALARM, CANCELLED 중 하나여야 합니다.");
        }
        requireOpen();
        this.status = closedStatus;
        this.resolvedAt = now();
        this.resolvedBy = by;
    }

    /** 오래 열려 있는 사건을 서버가 자동으로 닫는다 (종료자 없음) */
    public void autoClose() {
        requireOpen();
        this.status = EmergencyStatus.CANCELLED;
        this.resolvedAt = now();
        this.resolvedBy = null;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    public boolean isOwnedBy(Long userId) {
        return user.getUserId().equals(userId);
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new EmergencyStateException("이미 종료된 SOS입니다.");
        }
    }

    /** DB(timestamptz)에 저장되는 형태와 같은 값: UTC, 마이크로초 단위 (저장 직후 응답과 재조회 응답을 일치시키기 위함) */
    static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public Long getEmergencyId() {
        return emergencyId;
    }

    public User getUser() {
        return user;
    }

    public Long getWearableId() {
        return wearableId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public Long getSegmentId() {
        return segmentId;
    }

    public EmergencyType getType() {
        return type;
    }

    public EmergencySource getSource() {
        return source;
    }

    public EmergencyStatus getStatus() {
        return status;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }
}
