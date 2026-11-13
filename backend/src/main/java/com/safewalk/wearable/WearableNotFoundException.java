package com.safewalk.wearable;

/** 기기가 없거나 내 기기가 아님. GlobalExceptionHandler가 404로 변환한다. */
public class WearableNotFoundException extends RuntimeException {

    public WearableNotFoundException(String message) {
        super(message);
    }
}
