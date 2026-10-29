package com.safewalk.notification;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 개발용 푸시 발송기. 실제로 보내지 않고 서버 로그에 출력한 뒤 모두 성공으로 처리한다.
 * FIREBASE_CREDENTIALS_PATH가 비어 있으면 사용된다 ({@link PushSenderConfig}).
 */
public class ConsolePushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ConsolePushSender.class);

    @Override
    public String mode() {
        return "CONSOLE";
    }

    @Override
    public PushResult send(List<String> tokens, PushMessage message) {
        if (tokens.isEmpty()) {
            return PushResult.empty(mode());
        }
        log.info("[푸시(콘솔)] 기기 {}대 | 제목: {} | 내용: {} | data: {}",
                tokens.size(), message.title(), message.body(), message.data());
        return new PushResult(mode(), tokens, List.of(), List.of(), List.of());
    }
}
