package com.safewalk.guardian

import org.json.JSONObject

data class Guardian(
    val id: Long,
    val name: String,
    val phone: String,
    val relationship: String,
    val relationshipLabel: String,
    val isPrimary: Boolean,
    val status: String,
    val canViewLocation: Boolean,
)

data class GuardianInvitation(
    val guardianId: Long,
    val wardName: String,
    val relationshipLabel: String,
)

data class Ward(
    val guardianId: Long,
    val wardName: String,
    val wardPhone: String,
    val relationshipLabel: String,
    val isPrimary: Boolean,
    val canViewLocation: Boolean,
)

data class GuardianDraft(
    val name: String,
    val phone: String,
    val relationship: String,
    val isPrimary: Boolean = false,
    val canViewLocation: Boolean = false,
)

internal fun JSONObject.toGuardian() = Guardian(
    id = getLong("guardianId"),
    name = getString("name"),
    phone = getString("phone"),
    relationship = getString("relationship"),
    relationshipLabel = optString("relationshipLabel", getString("relationship")),
    isPrimary = optBoolean("isPrimary"),
    status = optString("registrationStatus", "UNREGISTERED"),
    canViewLocation = optBoolean("canViewLocation"),
)

internal fun JSONObject.toInvitation() = GuardianInvitation(
    guardianId = getLong("guardianId"),
    wardName = getString("wardName"),
    relationshipLabel = optString("relationshipLabel", optString("relationship")),
)

internal fun JSONObject.toWard() = Ward(
    guardianId = getLong("guardianId"),
    wardName = getString("wardName"),
    wardPhone = getString("wardPhone"),
    relationshipLabel = optString("relationshipLabel", optString("relationship")),
    isPrimary = optBoolean("isPrimary"),
    canViewLocation = optBoolean("canViewLocation"),
)
