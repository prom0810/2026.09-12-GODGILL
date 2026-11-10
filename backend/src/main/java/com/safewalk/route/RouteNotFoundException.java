package com.safewalk.route;

/** 경로가 없거나 내 경로가 아님. GlobalExceptionHandler가 404로 변환한다. */
public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException(String message) {
        super(message);
    }
}
