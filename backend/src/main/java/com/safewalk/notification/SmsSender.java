package com.safewalk.notification;

/**
 * 문자 발송기. 앱에 가입하지 않은(UNREGISTERED) 보호자에게 SOS 시작·종료를 알릴 때 사용한다.
 * 보호자 인증번호 문자에도 쓴다.
 * 구현: {@link SolapiSmsSender}(실제 발송), {@link ConsoleSmsSender}(개발용 로그 출력). 선택은 PushSenderConfig.
 */
public interface SmsSender {

    /** @param phone "010-1234-5678" 형태로 정규화된 번호 */
    void send(String phone, String text);
}
