package com.safewalk.notification;

import com.safewalk.device.UserDevice;
import com.safewalk.device.UserDeviceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 활성 기기 전체에 푸시를 보내고, FCM이 "쓸 수 없는 토큰"이라고 답한 기기를 비활성화한다.
 *
 * 항상 새 트랜잭션(REQUIRES_NEW)으로 실행한다. {@link NotificationService}는 알림 기록이 커밋된 "뒤에" 이 메서드를
 * 부르는데, 커밋이 끝난 트랜잭션에 이어 붙으면 토큰 비활성화가 DB에 반영되지 않기 때문이다.
 */
@Component
public class PushDelivery {

    private static final Logger log = LoggerFactory.getLogger(PushDelivery.class);

    private final UserDeviceRepository deviceRepository;
    private final PushSender pushSender;

    public PushDelivery(UserDeviceRepository deviceRepository, PushSender pushSender) {
        this.deviceRepository = deviceRepository;
        this.pushSender = pushSender;
    }

    public String mode() {
        return pushSender.mode();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
}
