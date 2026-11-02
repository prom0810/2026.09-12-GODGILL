package com.safewalk.emergency;

import java.time.OffsetDateTime;

/** SOS API 응답 */
public final class EmergencyResponses {

    private EmergencyResponses() {
    }

    /** 요청한 사람이 이 사건에서 어떤 입장인지 */
    public enum Role {
        SELF,
        GUARDIAN
    }

    /** POST /api/emergencies — 누구에게 알렸는지 */
    public record Notified(int pushGuardians, int locationSharedGuardians, int smsGuardians) {
    }

    public record Started(Long emergencyId, EmergencyStatus status, OffsetDateTime occurredAt, Notified notified) {
    }

    public record ResolvedBy(Long userId, Role role) {
    }

    /** PATCH /api/emergencies/{id} */
    public record Resolved(Long emergencyId, EmergencyStatus status, OffsetDateTime resolvedAt, ResolvedBy resolvedBy) {
    }

    public record InitialLocation(double lat, double lng) {
    }

    /**
     * GET /api/emergencies/{id}, GET /api/emergencies/active
     * - viewer: 요청한 사람이 SOS 당사자(SELF)인지 보호자(GUARDIAN)인지
     * - canViewLocation: 보호자가 위치를 볼 수 있는지 (당사자는 항상 true). false면 initialLocation은 null
     */
    public record Detail(
            Long emergencyId,
            Long userId,
            String userName,
            EmergencyType type,
            EmergencySource source,
            EmergencyStatus status,
            OffsetDateTime occurredAt,
            OffsetDateTime resolvedAt,
            ResolvedBy resolvedBy,
            InitialLocation initialLocation,
            Role viewer,
            boolean canViewLocation) {
    }

    /** GET /api/emergencies/shared — 보호 중인 사람들의 진행 중 SOS */
    public record Shared(
            Long emergencyId,
            Long guardianId,
            Long userId,
            String userName,
            EmergencyType type,
            EmergencySource source,
            OffsetDateTime occurredAt,
            boolean canViewLocation) {
    }
}
