package com.safewalk.wearable;

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
 * 사용자가 등록한 웨어러블 기기 (DB v3 wearable_device).
 *
 * - deviceIdentifier: 기기 고유값. Galaxy Watch는 워치의 ANDROID_ID(재설치해도 같고 공장 초기화하면 바뀜),
 *   M5StickC는 ESP32 MAC 주소를 쓸 예정. 같은 계정 안에서만 중복 불가 (uq_wearable_identifier_active).
 *   같은 워치를 여러 계정에 등록하는 것은 허용
 * - active: 지금 앱과 연결 중인 기기. 사용자당 최대 1대 (uq_wearable_one_active),
 *   워치 1대당 최대 1계정 (uq_wearable_identifier_connected). 등록만으로는 연결되지 않는다
 * - connectedAt: 처음 등록한 시각, lastConnectedAt: 마지막 연결 상태 변경 시각 (연결·해제·앱 재시작 확인)
 * - 삭제는 soft delete (deleted_at 기록, 연결 해제. DB 제약 ck_wearable_deleted_inactive)
 */
@Entity
@Table(name = "wearable_device")
public class WearableDevice {

    public static final int NAME_MAX = 100;
    public static final int IDENTIFIER_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wearable_id")
    private Long wearableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", length = NAME_MAX)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 50)
    private WearableType deviceType;

    @Column(name = "device_identifier", nullable = false, length = IDENTIFIER_MAX)
    private String deviceIdentifier;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "connected_at", nullable = false)
    private OffsetDateTime connectedAt;

    @Column(name = "last_connected_at")
    private OffsetDateTime lastConnectedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected WearableDevice() {
    }

    public WearableDevice(User user, WearableType deviceType, String deviceIdentifier, String deviceName) {
        OffsetDateTime now = OffsetDateTime.now();
        this.user = user;
        this.deviceType = deviceType;
        this.deviceIdentifier = deviceIdentifier;
        this.deviceName = deviceName;
        this.active = false;
        this.connectedAt = now;
        this.lastConnectedAt = now;
    }

    public Long getWearableId() {
        return wearableId;
    }

    public User getUser() {
        return user;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public WearableType getDeviceType() {
        return deviceType;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getConnectedAt() {
        return connectedAt;
    }

    public OffsetDateTime getLastConnectedAt() {
        return lastConnectedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    /** 재등록 때 이름을 보냈으면 이름만 바꾼다 (연결 상태·시각은 그대로) */
    public void rename(String newName) {
        if (newName != null) {
            this.deviceName = newName;
        }
    }

    /** 연결. 이미 연결 중이면 시각만 갱신한다 (앱 재시작·안내 시작 때 확인) */
    public void connect(OffsetDateTime now) {
        this.active = true;
        this.lastConnectedAt = now;
    }

    /** 연결 해제. 이미 해제돼 있으면 아무것도 바꾸지 않는다 */
    public boolean disconnect(OffsetDateTime now) {
        if (!active) {
            return false;
        }
        this.active = false;
        this.lastConnectedAt = now;
        return true;
    }

    public void delete(OffsetDateTime now) {
        disconnect(now);
        this.deletedAt = now;
    }
}
