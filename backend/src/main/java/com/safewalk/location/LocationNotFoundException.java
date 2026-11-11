package com.safewalk.location;

public class LocationNotFoundException extends RuntimeException {
    public LocationNotFoundException() {
        super("위치 공유 세션을 찾을 수 없거나 열람 권한이 없습니다.");
    }
}
