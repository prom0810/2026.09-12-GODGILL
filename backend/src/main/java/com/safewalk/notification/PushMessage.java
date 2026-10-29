package com.safewalk.notification;

import java.util.Map;

/**
 * 푸시 알림 한 건의 내용.
 *
 * - title, body: 알림창에 표시되는 제목과 내용
 * - data: 앱이 받아서 처리할 값 (예: type=EMERGENCY_STARTED, emergencyId=12). FCM 규칙상 값은 모두 문자열이다.
 */
public record PushMessage(String title, String body, Map<String, String> data) {

    public PushMessage {
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static PushMessage of(String title, String body) {
        return new PushMessage(title, body, Map.of());
    }
}
