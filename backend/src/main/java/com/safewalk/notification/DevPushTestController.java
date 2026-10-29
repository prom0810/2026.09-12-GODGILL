package com.safewalk.notification;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import com.safewalk.user.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * (개발용) 내 기기로 테스트 푸시 보내기. .env에 DEV_PUSH_TEST_ENABLED=true일 때만 생긴다 (없으면 404).
 *
 *  POST /api/dev/push-test   { "title": "...", "body": "..." }   둘 다 선택
 *
 * 응답의 mode가 FCM인데 errorCodes에 THIRD_PARTY_AUTH_ERROR / PERMISSION_DENIED 등이 있으면
 * 서비스 계정 키나 Firebase 프로젝트 설정 문제이고, UNREGISTERED / INVALID_ARGUMENT면
 * 서버 인증은 정상이고 토큰만 잘못된 것이다(가짜 토큰으로 서버 설정을 확인할 때 이 차이를 본다).
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "dev.push-test-enabled", havingValue = "true")
public class DevPushTestController {

    private final NotificationService notificationService;
    private final UserService userService;

    public DevPushTestController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    public record PushTestRequest(String title, String body) {
    }

    public record PushTestResponse(
            String mode,
            int deviceCount,
            int successCount,
            int deactivatedCount,
            int failedCount,
            List<String> errorCodes) {
    }

    @PostMapping("/push-test")
    public ApiResponse<PushTestResponse> pushTest(@LoginUser Long userId,
                                                  @RequestBody(required = false) PushTestRequest request) {
        userService.getActiveUser(userId);
        String title = request != null && request.title() != null && !request.title().isBlank()
                ? request.title() : "안심동행 테스트 알림";
        String body = request != null && request.body() != null && !request.body().isBlank()
                ? request.body() : "푸시 알림이 정상적으로 도착했습니다.";

        PushResult result = notificationService.pushToUser(
                userId, new PushMessage(title, body, Map.of("type", "TEST")));

        int deviceCount = result.successTokens().size() + result.invalidTokens().size() + result.failedTokens().size();
        return ApiResponse.ok(new PushTestResponse(
                result.mode(),
                deviceCount,
                result.successTokens().size(),
                result.invalidTokens().size(),
                result.failedTokens().size(),
                result.errorCodes()));
    }
}
