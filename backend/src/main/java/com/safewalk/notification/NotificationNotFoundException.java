package com.safewalk.notification;

/** 없는 알림이거나 다른 사용자의 알림. GlobalExceptionHandler가 404로 변환한다. */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String message) {
        super(message);
    }
}
