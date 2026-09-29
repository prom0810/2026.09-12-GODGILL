package com.safewalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 안심동행 백엔드 서버의 진입점.
 * com.safewalk 하위 패키지(auth, user, route, notification, emergency, datalog, global)를
 * 컴포넌트 스캔 대상으로 포함한다.
 */
@SpringBootApplication
public class SafewalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafewalkApplication.class, args);
    }
}
