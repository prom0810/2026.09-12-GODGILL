package com.safewalk.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 개발용 로컬 디스크 구현. STORAGE_TYPE이 없거나 local이면 사용된다.
 *
 * Supabase 키가 없는 팀원도 백엔드를 실행하고 사진 API를 시험할 수 있게 하기 위한 것이다.
 * 파일은 STORAGE_LOCAL_DIR(기본 backend/local-storage)에 저장되고,
 * {@link LocalStorageWebConfig}가 /local-storage/** 주소로 내려준다.
 *
 * 개발 전용: 만료 시간이 적용되지 않고, 주소를 아는 사람은 누구나 열 수 있다. 배포 환경에서 쓰지 말 것.
 */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    /** {@link LocalStorageWebConfig}와 공유하는 URL 경로 */
    public static final String URL_PREFIX = "/local-storage/";

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);

    private final Path root;

    public LocalImageStorage(@Value("${storage.local-dir}") String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("로컬 이미지 저장 폴더를 만들 수 없습니다: " + root, e);
        }
        log.warn("이미지 저장소: 로컬 폴더 {} (개발용). Supabase를 쓰려면 .env에 STORAGE_TYPE=supabase를 넣으세요.", root);
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new ImageStorageException("이미지를 로컬 저장소에 저장하지 못했습니다.", e);
        }
    }

    @Override
    public String createViewUrl(String key, Duration ttl) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(URL_PREFIX)
                .path(key)
                .toUriString();
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new ImageStorageException("로컬 저장소에서 이미지를 삭제하지 못했습니다.", e);
        }
    }

    /** key가 저장 폴더 밖을 가리키지 못하게 막는다. */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 저장 경로입니다.");
        }
        return target;
    }
}
