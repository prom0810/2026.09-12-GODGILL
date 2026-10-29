package com.safewalk.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Firebase Cloud Messaging 발송기 (Firebase Admin SDK, HTTP v1 API).
 *
 * .env의 FIREBASE_CREDENTIALS_PATH가 가리키는 서비스 계정 키(JSON)로 인증한다.
 * 이 키는 Firebase 프로젝트 관리자 권한이므로 서버에만 두고 절대 커밋하지 않는다.
 *
 * - 알림(notification) + 데이터(data)를 함께 보낸다. 앱이 꺼져 있으면 Android가 알림창에 바로 표시하고,
 *   앱이 켜져 있으면 FirebaseMessagingService.onMessageReceived에서 data를 받아 처리한다.
 * - Android 우선순위를 HIGH로 보내 절전(Doze) 상태에서도 바로 도착하게 한다 (SOS 알림용).
 */
public class FcmPushSender implements PushSender, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    /** FCM sendEach 한 번에 보낼 수 있는 최대 메시지 수 */
    private static final int MAX_BATCH = 500;

    /** 이 오류가 나면 토큰을 더 이상 쓸 수 없다고 보고 비활성화한다. */
    private static final Set<MessagingErrorCode> INVALID_TOKEN_CODES = Set.of(
            MessagingErrorCode.UNREGISTERED,       // 앱 삭제, 토큰 만료
            MessagingErrorCode.INVALID_ARGUMENT,   // 토큰 형식이 잘못됨
            MessagingErrorCode.SENDER_ID_MISMATCH  // 다른 Firebase 프로젝트의 토큰
    );

    private final FirebaseApp app;
    private final FirebaseMessaging messaging;

    public FcmPushSender(Path credentialsPath) {
        if (!Files.isRegularFile(credentialsPath)) {
            throw new IllegalStateException(
                    "FIREBASE_CREDENTIALS_PATH 파일을 찾을 수 없습니다: " + credentialsPath.toAbsolutePath()
                            + " (상대 경로는 서버를 실행한 폴더 기준. 푸시 없이 실행하려면 .env에서 이 값을 지우세요.)");
        }
        try (InputStream in = Files.newInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            // 이름 있는 앱으로 만들어 다른 라이브러리의 기본 앱과 겹치지 않게 한다.
            this.app = FirebaseApp.initializeApp(options, "safewalk");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Firebase 서비스 계정 키를 읽지 못했습니다. 콘솔에서 받은 JSON 파일이 맞는지 확인하세요: "
                            + credentialsPath.toAbsolutePath(), e);
        }
        this.messaging = FirebaseMessaging.getInstance(app);
        log.info("푸시 발송: FCM 사용 (Firebase 프로젝트 {})", app.getOptions().getProjectId());
    }

    @Override
    public String mode() {
        return "FCM";
    }

    @Override
    public PushResult send(List<String> tokens, PushMessage message) {
        if (tokens.isEmpty()) {
            return PushResult.empty(mode());
        }
        List<String> success = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Set<String> errorCodes = new LinkedHashSet<>();

        for (int from = 0; from < tokens.size(); from += MAX_BATCH) {
            List<String> chunk = tokens.subList(from, Math.min(from + MAX_BATCH, tokens.size()));
            List<Message> messages = chunk.stream().map(token -> build(token, message)).toList();
            try {
                BatchResponse batch = messaging.sendEach(messages);
                List<SendResponse> responses = batch.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse r = responses.get(i);
                    String token = chunk.get(i);
                    if (r.isSuccessful()) {
                        success.add(token);
                        continue;
                    }
                    FirebaseMessagingException e = r.getException();
                    errorCodes.add(codeOf(e));
                    if (e != null && INVALID_TOKEN_CODES.contains(e.getMessagingErrorCode())) {
                        invalid.add(token);
                    } else {
                        failed.add(token);
                        log.warn("FCM 발송 실패 (토큰 끝자리 {}): {}", tail(token), e == null ? "원인 없음" : e.getMessage());
                    }
                }
            } catch (FirebaseMessagingException e) {
                // 묶음 전체 실패 (인증 오류, 네트워크 등). 토큰 문제가 아니므로 비활성화하지 않는다.
                errorCodes.add(codeOf(e));
                failed.addAll(chunk);
                log.error("FCM 발송 요청 실패 ({}대): {}", chunk.size(), e.getMessage(), e);
            }
        }
        return new PushResult(mode(), success, invalid, failed, List.copyOf(errorCodes));
    }

    private static Message build(String token, PushMessage message) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .build();
    }

    private static String codeOf(FirebaseMessagingException e) {
        if (e == null) {
            return "UNKNOWN";
        }
        if (e.getMessagingErrorCode() != null) {
            return e.getMessagingErrorCode().name();
        }
        ErrorCode code = e.getErrorCode();
        return code != null ? code.name() : "UNKNOWN";
    }

    /** 로그에 토큰 전체를 남기지 않는다. */
    static String tail(String token) {
        return token.length() <= 8 ? token : "…" + token.substring(token.length() - 8);
    }

    @Override
    public void destroy() {
        app.delete();
    }
}
