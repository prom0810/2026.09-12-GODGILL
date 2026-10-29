package com.safewalk.device;

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

/**
 * 푸시를 받는 앱 설치 기기 (DB v3 user_device 테이블, users와 N:1).
 *
 * - fcmToken: 기기(앱 설치)마다 하나. 전체 테이블에서 UNIQUE (DB uq_user_device_fcm_token)
 * - 같은 폰에서 다른 계정으로 로그인하면 토큰은 그대로이고 주인만 바뀐다 → {@link #assignTo}
 * - 로그아웃하거나 FCM이 "더 이상 없는 토큰"이라고 알려 주면 active=false (행은 남김)
 */
@Entity
@Table(name = "user_device")
public class UserDevice {

    /** DB 컬럼 길이 (fcm_token VARCHAR(512)) */
    public static final int MAX_TOKEN_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_device_id")
    private Long userDeviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "fcm_token", nullable = false, unique = true, length = MAX_TOKEN_LENGTH)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 30)
    private DeviceType deviceType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 전용 기본 생성자 */
    protected UserDevice() {
    }

    public UserDevice(User user, String fcmToken, DeviceType deviceType) {
        this.user = user;
        this.fcmToken = fcmToken;
        this.deviceType = deviceType;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
    }

    /** 다시 등록됨: 주인을 이 사용자로 바꾸고 활성화한다. 기기 종류는 보낸 경우에만 바꾼다. */
    public void assignTo(User user, DeviceType deviceType) {
        this.user = user;
        if (deviceType != null) {
            this.deviceType = deviceType;
        }
        this.active = true;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 로그아웃 또는 토큰 만료 */
    public void deactivate() {
        if (active) {
            this.active = false;
            this.updatedAt = OffsetDateTime.now();
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user.getUserId().equals(userId);
    }

    public Long getUserDeviceId() {
        return userDeviceId;
    }

    public User getUser() {
        return user;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
