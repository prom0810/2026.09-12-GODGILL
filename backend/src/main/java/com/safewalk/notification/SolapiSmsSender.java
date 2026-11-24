package com.safewalk.notification;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.model.FailedMessage;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SOLAPI로 실제 문자를 보내는 발송기.
 *
 * - 발신번호는 SOLAPI 콘솔에 사전 등록된 번호만 쓸 수 있다 (전기통신사업법 발신번호 사전등록제).
 * - SOLAPI는 번호를 "01012345678"처럼 숫자만 받으므로, 우리 쪽 정규화 형식("010-1234-5678")에서 하이픈을 뺀다.
 * - 본문이 한글 45자(영문 90자)를 넘으면 SOLAPI가 자동으로 LMS(장문)로 보낸다. LMS는 단가가 더 비싸다.
 * - 발송에 실패해도 예외를 던지지 않고 로그만 남긴다. 문자는 트랜잭션 커밋 뒤에 나가므로
 *   (NotificationService.sendSms) 여기서 예외를 던져도 이미 저장된 SOS·인증번호 기록은 되돌릴 수 없고,
 *   요청한 사용자에게 오류만 보이게 된다. 푸시 발송(pushQuietly)과 같은 방식이다.
 * - 로그에는 전화번호 가운데를 가려서 남긴다.
 */
public class SolapiSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SolapiSmsSender.class);

    private final DefaultMessageService messageService;
    private final String senderNumber;

    public SolapiSmsSender(String apiKey, String apiSecret, String senderNumber) {
        this.messageService = SolapiClient.INSTANCE.createInstance(apiKey, apiSecret);
        this.senderNumber = digitsOnly(senderNumber);
    }

    @Override
    public void send(String phone, String text) {
        String to = digitsOnly(phone);
        if (to.isEmpty()) {
            log.warn("[문자(SOLAPI)] 받는 번호가 비어 있어 보내지 않음");
            return;
        }

        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(to);
        message.setText(text);

        try {
            MultipleDetailMessageSentResponse response = messageService.send(message);
            if (!response.getFailedMessageList().isEmpty()) {
                logFailures(phone, response.getFailedMessageList());
                return;
            }
            String groupId = response.getGroupInfo() == null ? null : response.getGroupInfo().getGroupId();
            log.info("[문자(SOLAPI)] 접수 완료 받는 사람: {} | groupId: {}", ConsoleSmsSender.mask(phone), groupId);
        } catch (SolapiMessageNotReceivedException e) {
            // 모든 메시지가 접수 단계에서 거절된 경우 (발신번호 미등록, 잔액 부족 등)
            logFailures(phone, e.getFailedMessageList());
        } catch (Exception e) {
            // API 키 오류, 허용되지 않은 IP, 네트워크 오류 등
            log.error("[문자(SOLAPI)] 발송 오류 받는 사람: {} | {}: {}",
                    ConsoleSmsSender.mask(phone), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static void logFailures(String phone, java.util.List<FailedMessage> failures) {
        if (failures == null || failures.isEmpty()) {
            log.error("[문자(SOLAPI)] 발송 실패 받는 사람: {} | 실패 사유 없음", ConsoleSmsSender.mask(phone));
            return;
        }
        for (FailedMessage failed : failures) {
            // statusCode 예: 1062 = 발신번호 미등록
            log.error("[문자(SOLAPI)] 발송 실패 받는 사람: {} | 코드: {} | 사유: {}",
                    ConsoleSmsSender.mask(phone), failed.getStatusCode(), failed.getStatusMessage());
        }
    }

    /** "010-1234-5678", "010 1234 5678" → "01012345678" */
    static String digitsOnly(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }
}
