package com.safewalk.notification;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 푸시·문자 발송기 선택.
 *
 * - FIREBASE_CREDENTIALS_PATH가 있으면 {@link FcmPushSender}. 파일이 없거나 잘못되면 서버가 시작되지 않는다
 *   (조용히 콘솔로 바뀌어 "푸시가 안 오는데 이유를 모르는" 상황을 막기 위함).
 * - 비어 있으면 {@link ConsolePushSender}. 이미지 저장소의 STORAGE_TYPE=local과 같은 역할이다.
 * - 문자는 MVP 동안 항상 {@link ConsoleSmsSender} (실제 SMS는 유료라 보류).
 */
@Configuration
public class PushSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(PushSenderConfig.class);

    @Bean
    public PushSender pushSender(@Value("${firebase.credentials-path:}") String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("푸시 발송: 콘솔 출력(개발용). 실제로 보내려면 .env에 FIREBASE_CREDENTIALS_PATH를 넣으세요.");
            return new ConsolePushSender();
        }
        return new FcmPushSender(Path.of(credentialsPath.trim()));
    }

    @Bean
    public SmsSender smsSender() {
        return new ConsoleSmsSender();
    }
}
