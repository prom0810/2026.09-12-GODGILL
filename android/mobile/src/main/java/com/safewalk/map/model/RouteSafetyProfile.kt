package com.safewalk.map.model

import android.content.Context
import com.safewalk.common.network.AuthenticatedApiClient
import org.json.JSONObject
import java.io.IOException

enum class RouteAgeBand(val label: String) {
    UNDER_14("14세 미만"), AGE_14_PLUS("14세 이상"),
}

/** 시설 바로 옆에서 적용할 최대 보너스. 거리 감쇠는 RouteSafetyScorer가 담당한다. */
data class RouteSafetyWeights(val facilityWeights: Map<String, Double>) {
    init {
        require(facilityWeights.keys.containsAll(FACILITY_TYPES)) { "시설 가중치가 누락되었습니다." }
        require(facilityWeights.values.all { it.isFinite() && it >= 0.0 }) { "시설 가중치가 올바르지 않습니다." }
        require(facilityWeights.values.sum() <= MAX_TOTAL_BONUS + 1e-9) { "시설 보너스 합계는 0.10 이하여야 합니다." }
    }

    fun bonus(type: String): Double = facilityWeights.getValue(type)

    companion object {
        val FACILITY_TYPES = setOf("CCTV", "STREET_LIGHT", "POLICE", "EMERGENCY_BELL", "CONVENIENCE_STORE", "FIRE_STATION")
        const val MAX_TOTAL_BONUS = 0.10
        // 모든 연령대와 비회원의 공통 기준. 연령 보정은 검증된 입력이 생기기 전까지 0이다.
        val DEFAULT = RouteSafetyWeights(FACILITY_TYPES.associateWith {
            when (it) { "CCTV" -> 0.02; "STREET_LIGHT", "POLICE" -> 0.04; else -> 0.0 }
        })
    }
}

data class RouteSafetyProfile(val ageBand: RouteAgeBand?, val weights: RouteSafetyWeights) {
    companion object {
        fun fromJson(data: JSONObject): RouteSafetyProfile {
            if (data.getBoolean("birthDateRequired")) throw BirthDateRequiredException()
            val ageBand = RouteAgeBand.valueOf(data.getString("ageBand"))
            val values = data.getJSONObject("facilityWeights")
            return RouteSafetyProfile(ageBand, RouteSafetyWeights(
                RouteSafetyWeights.FACILITY_TYPES.associateWith { values.getDouble(it) },
            ))
        }
    }
}

class BirthDateRequiredException : IOException("경로 안내용 연령 정보를 확인하려면 생년월일을 입력해 주세요.")

class RouteSafetyProfileRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun load(): RouteSafetyProfile = RouteSafetyProfile.fromJson(api.getObject("/api/routes/safety-profile"))

    fun updateBirthDate(birthDate: String) {
        api.patchObject("/api/users/me/birth-date", JSONObject().put("birthDate", birthDate))
    }
}
