package com.safewalk.emergency;

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

/**
 * 긴급 사건 처리 이력 한 줄 (DB v3 emergency_event_log). 사건이 삭제되면 함께 삭제된다 (ON DELETE CASCADE).
 * 앱에 보여 주는 용도가 아니라, 무슨 일이 언제 있었는지 서버 쪽에서 추적하기 위한 기록이다.
 */
@Entity
@Table(name = "emergency_event_log")
public class EmergencyEventLog {

    /** DB 컬럼 길이 (message VARCHAR(500)) */
    public static final int MAX_MESSAGE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_log_id")
    private Long eventLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emergency_id", nullable = false)
    private EmergencyEvent emergency;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EmergencyLogType eventType;

    @Column(name = "message", length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = EmergencyEvent.now();

    /** JPA 전용 기본 생성자 */
    protected EmergencyEventLog() {
    }

    public EmergencyEventLog(EmergencyEvent emergency, EmergencyLogType eventType, String message) {
        this.emergency = emergency;
        this.eventType = eventType;
        this.message = message == null || message.length() <= MAX_MESSAGE_LENGTH
                ? message : message.substring(0, MAX_MESSAGE_LENGTH);
        this.createdAt = EmergencyEvent.now();
    }

    public Long getEventLogId() {
        return eventLogId;
    }

    public EmergencyEvent getEmergency() {
        return emergency;
    }

    public EmergencyLogType getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
