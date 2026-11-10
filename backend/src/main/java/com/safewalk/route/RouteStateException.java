package com.safewalk.route;

/** 경로 상태 때문에 처리할 수 없음 (예: 이미 도착한 경로를 다시 종료). GlobalExceptionHandler가 409로 변환한다. */
public class RouteStateException extends RuntimeException {

    public RouteStateException(String message) {
        super(message);
    }
}
