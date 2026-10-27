package com.safewalk.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 개발용 문자 발송기. 실제로 보내지 않고 서버 로그에 출력한다.
 * 전화번호는 가운데를 가려서 남긴다.
 */
public class ConsoleSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);

    @Override
    public void send(String phone, String text) {
        log.info("[문자(콘솔)] 받는 사람: {} | 내용: {}", mask(phone), text);
    }

    static String mask(String phone) {
        if (phone == null) {
            return "";
        }
        // 010-1234-5678 → 010-****-5678
        return phone.replaceAll("^(\\d{2,3})-(\\d{3,4})-(\\d{4})$", "$1-****-$3");
    }
}
