package com.safewalk.storage;

/**
 * 이미지 저장소(Supabase Storage 등)와 통신하다 실패한 경우.
 * 사용자 입력 문제가 아니라 외부 저장소 문제이므로 502로 응답한다({@link com.safewalk.global.GlobalExceptionHandler}).
 */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
