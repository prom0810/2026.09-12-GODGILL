package com.safewalk.notification;

import com.safewalk.device.UserDevice;
import com.safewalk.device.UserDeviceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발송 창구. 다른 도메인(SOS, 경로 이탈 등)은 FCM을 직접 부르지 않고 이 서비스를 쓴다.
 *
 * - {@link #pushToUser}: 사용자의 활성 기기 전체에 푸시. FCM이 "없는 토큰"이라고 알려 준 기기는 비활성화한다.
 * - {@link #sendSms}: 문자 (MVP는 콘솔 출력).
 *
 * TODO(SOS): notification 테이블 기록, 보호자 대상 발송은 SOS 구현 때 추가.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserDeviceRepository deviceRepository;
    private final PushSender pushSender;
    private final SmsSender smsSender;

    public NotificationService(UserDeviceRepository deviceRepository, PushSender pushSender, SmsSender smsSender) {
        this.deviceRepository = deviceRepository;
        this.pushSender = pushSender;
        this.smsSender = smsSender;
    }

    /** 현재 발송 방식 (FCM / CONSOLE) */
    public String pushMode() {
        return pushSender.mode();
    }

    /**
     * 사용자의 활성 기기 모두에 푸시를 보낸다. 기기가 없으면 아무것도 보내지 않는다.
     * 발송 실패는 예외로 던지지 않고 결과에 담는다 (알림 실패 때문에 SOS 같은 본 작업이 실패하면 안 되므로).
     */
    @Transactional
    public PushResult pushToUser(Long userId, PushMessage message) {
        List<String> tokens = deviceRepository.findByUser_UserIdAndActiveTrueOrderByUserDeviceIdAsc(userId)
                .stream()
                .map(UserDevice::getFcmToken)
                .toList();
        PushResult result = pushSender.send(tokens, message);
        if (!result.invalidTokens().isEmpty()) {
            int count = deviceRepository.deactivateByTokens(result.invalidTokens(), OffsetDateTime.now());
            log.info("사용할 수 없는 FCM 토큰 {}개 비활성화 (userId={})", count, userId);
        }
        return result;
    }

    public void sendSms(String phone, String text) {
        smsSender.send(phone, text);
    }

    public void notifyRouteDeviation(Long userId) {
        // TODO: 경로 이탈 알림 발송 로직 구현
    }

    public void notifyGuardian(Long userId, String message) {
        // TODO: 보호자 알림 발송 로직 구현 (SOS 명세: claude/emergency-sos-api-v1.md)
    }
}
