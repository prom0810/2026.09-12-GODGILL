package com.safewalk.notification;

import java.util.List;

/**
 * 푸시 발송기. 구현은 {@link PushSenderConfig}가 하나만 고른다.
 *
 * - {@link FcmPushSender}: FIREBASE_CREDENTIALS_PATH가 있을 때. 실제 FCM 발송
 * - {@link ConsolePushSender}: 없을 때. 서버 로그에만 출력 (키 없는 팀원용)
 *
 * MSA 전환 시에는 이 인터페이스 뒤에서 Kafka로 알림 서비스에 넘기는 구현으로 바꿀 수 있다.
 */
public interface PushSender {

    /** FCM / CONSOLE */
    String mode();

    /**
     * 같은 내용을 여러 기기에 보낸다. 토큰 목록이 비어 있으면 아무것도 하지 않는다.
     * 개별 기기의 실패는 예외 대신 결과에 담는다.
     */
    PushResult send(List<String> tokens, PushMessage message);
}
