package com.safewalk.notification

import org.springframework.stereotype.Service

/**
 * 경로 이탈, 위험구간 진입, 보호자 알림 발송을 담당.
 * TODO: FCM 등 푸시 발송 연동.
 */
@Service
class NotificationService {

    fun notifyRouteDeviation(userId: Long) {
        // TODO: 경로 이탈 알림 발송 로직 구현
    }

    fun notifyGuardian(userId: Long, message: String) {
        // TODO: 보호자 알림 발송 로직 구현
    }
}
