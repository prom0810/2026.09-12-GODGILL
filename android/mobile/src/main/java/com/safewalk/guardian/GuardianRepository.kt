package com.safewalk.guardian

import android.content.Context
import com.safewalk.common.network.AuthenticatedApiClient
import org.json.JSONObject

class GuardianRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun guardians(): List<Guardian> = api.getArray("/api/guardians").mapObjects { it.toGuardian() }

    fun invitations(): List<GuardianInvitation> =
        api.getArray("/api/guardians/invitations").mapObjects { it.toInvitation() }

    fun wards(): List<Ward> = api.getArray("/api/guardians/wards").mapObjects { it.toWard() }

    fun add(draft: GuardianDraft): Guardian = api.postObject(
        "/api/guardians",
        JSONObject().apply {
            put("name", draft.name)
            put("phone", draft.phone)
            put("relationship", draft.relationship)
            put("isPrimary", draft.isPrimary)
            put("canViewLocation", draft.canViewLocation)
        },
    ).toGuardian()

    fun update(id: Long, draft: GuardianDraft): Guardian = api.patchObject(
        "/api/guardians/$id",
        JSONObject().apply {
            put("name", draft.name)
            put("relationship", draft.relationship)
            put("isPrimary", draft.isPrimary)
            put("canViewLocation", draft.canViewLocation)
        },
    ).toGuardian()

    fun invite(id: Long): Guardian = api.postObject("/api/guardians/$id/invite").toGuardian()
    fun revoke(id: Long): Guardian = api.postObject("/api/guardians/$id/revoke").toGuardian()
    fun delete(id: Long) = api.delete("/api/guardians/$id")
    fun accept(id: Long): Ward = api.postObject("/api/guardians/invitations/$id/accept").toWard()
    fun reject(id: Long) = api.post("/api/guardians/invitations/$id/reject")
    fun leave(id: Long) = api.post("/api/guardians/wards/$id/leave")
}

private inline fun <T> org.json.JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }
