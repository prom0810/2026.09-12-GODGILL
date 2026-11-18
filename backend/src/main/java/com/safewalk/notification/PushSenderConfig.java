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
 * - 문자: SOLAPI_API_KEY가 있으면 {@link SolapiSmsSender}로 실제 발송. 이때 SOLAPI_API_SECRET·SOLAPI_SENDER가
 *   비어 있으면 서버가 시작되지 않는다 (푸시와 같은 이유). 비어 있으면 {@link ConsoleSmsSender} (서버 로그 출력).
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
    public SmsSender smsSender(@Value("${sms.solapi.api-key:}") String apiKey,
                               @Value("${sms.solapi.api-secret:}") String apiSecret,
                               @Value("${sms.solapi.sender:}") String sender) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("문자 발송: 콘솔 출력(개발용). 실제로 보내려면 .env에 SOLAPI_API_KEY·SOLAPI_API_SECRET·SOLAPI_SENDER를 넣으세요.");
            return new ConsoleSmsSender();
        }
        if (apiSecret == null || apiSecret.isBlank() || sender == null || sender.isBlank()) {
            throw new IllegalStateException(
                    "SOLAPI_API_KEY가 설정되어 있으면 SOLAPI_API_SECRET과 SOLAPI_SENDER(등록한 발신번호)도 필요합니다.");
        }
        log.info("문자 발송: SOLAPI");
        return new SolapiSmsSender(apiKey.trim(), apiSecret.trim(), sender.trim());
    }
}
