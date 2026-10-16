package com.safewalk.storage;

import java.time.Duration;

/**
 * 이미지 파일 저장소.
 *
 * DB(risk_report_image.storage_path)에는 URL이 아니라 이 인터페이스가 쓰는 object key만 저장한다.
 * 예: "reports/12/3f2a...e1.jpg"
 * 화면에 보여줄 URL은 조회할 때마다 {@link #createViewUrl}로 새로 만든다(만료 시간이 있는 URL).
 *
 * 구현체는 application.yml의 storage.type 값으로 하나만 선택된다.
 *  - supabase : {@link SupabaseImageStorage} (Supabase Storage, S3 호환 API)
 *  - local    : {@link LocalImageStorage} (개발용, 서버 PC의 폴더에 저장. 기본값)
 */
public interface ImageStorage {

    /** 파일을 key 위치에 저장한다. 같은 key가 있으면 덮어쓴다. */
    void upload(String key, byte[] bytes, String contentType);

    /** 일정 시간 동안만 열람 가능한 URL을 만든다. */
    String createViewUrl(String key, Duration ttl);

    /** 파일을 삭제한다. 없는 key여도 예외를 던지지 않는다. */
    void delete(String key);
}
