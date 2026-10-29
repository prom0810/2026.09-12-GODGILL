package com.safewalk.notification;

/**
 * 문자 발송기. 앱에 가입하지 않은(UNREGISTERED) 보호자에게 SOS 시작·종료를 알릴 때 사용한다.
 * MVP에서는 {@link ConsoleSmsSender}(서버 로그 출력)만 있다. 실제 SMS 서비스를 붙일 때 구현을 추가한다.
 */
public interface SmsSender {

    /** @param phone "010-1234-5678" 형태로 정규화된 번호 */
    void send(String phone, String text);
}
