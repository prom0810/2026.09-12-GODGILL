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
    }

    fun bonus(type: String): Double = facilityWeights.getValue(type)

    companion object {
        val FACILITY_TYPES = setOf("CCTV", "STREET_LIGHT", "POLICE", "EMERGENCY_BELL", "CONVENIENCE_STORE", "FIRE_STATION")
        // 비회원은 기존의 기본 계산을 사용한다. 로그인 사용자는 서버 값을 필수로 조회한다.
        val DEFAULT = RouteSafetyWeights(FACILITY_TYPES.associateWith { if (it == "STREET_LIGHT") 0.10 else 0.15 })
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

class BirthDateRequiredException : IOException("연령에 맞는 경로를 안내하려면 생년월일을 입력해 주세요.")

class RouteSafetyProfileRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun load(): RouteSafetyProfile = RouteSafetyProfile.fromJson(api.getObject("/api/routes/safety-profile"))

    fun updateBirthDate(birthDate: String) {
        api.patchObject("/api/users/me/birth-date", JSONObject().put("birthDate", birthDate))
    }
}
