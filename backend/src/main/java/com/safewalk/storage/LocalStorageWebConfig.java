package com.safewalk.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 저장소 모드에서만 /local-storage/** 로 저장된 파일을 내려준다.
 * /api/** 밖이라 JWT 검사를 받지 않는다(개발 전용).
 */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final LocalImageStorage localImageStorage;

    public LocalStorageWebConfig(LocalImageStorage localImageStorage) {
        this.localImageStorage = localImageStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = localImageStorage.getRoot().toUri().toString();
        // 폴더 위치는 반드시 "/"로 끝나야 그 아래 파일로 인식된다.
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(LocalImageStorage.URL_PREFIX + "**")
                .addResourceLocations(location);
    }
}
