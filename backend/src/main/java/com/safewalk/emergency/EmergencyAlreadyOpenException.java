package com.safewalk.emergency;

/**
 * 이미 진행 중인 SOS가 있는데 새로 시작하려는 경우 (사용자당 1건).
 * GlobalExceptionHandler가 409 + data.emergencyId(기존 사건)로 응답한다 → 앱은 그 사건으로 이어서 진행한다.
 */
public class EmergencyAlreadyOpenException extends RuntimeException {

    private final Long emergencyId;

    public EmergencyAlreadyOpenException(Long emergencyId) {
        super("이미 진행 중인 SOS가 있습니다.");
        this.emergencyId = emergencyId;
    }

    public Long getEmergencyId() {
        return emergencyId;
    }
}
