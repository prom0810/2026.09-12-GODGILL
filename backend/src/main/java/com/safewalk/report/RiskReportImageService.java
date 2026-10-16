package com.safewalk.report;

import com.safewalk.storage.ImageStorage;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 제보 사진 검사·저장·삭제와 열람 URL 생성.
 *
 * 저장 순서와 실패 처리:
 *  1. 파일을 모두 검사한다(형식·크기). 하나라도 문제면 저장소와 DB에 아무것도 쓰지 않는다.
 *  2. 저장소에 올리고 risk_report_image 행을 저장한다(호출한 쪽 트랜잭션 안에서).
 *  3. 트랜잭션이 롤백되면 이미 올린 파일을 저장소에서 지운다(보상 삭제).
 *  삭제는 반대로 DB 커밋이 끝난 뒤 저장소 파일을 지운다. 파일 삭제가 실패하면 로그만 남긴다
 *  (파일이 남는 쪽이, DB 행은 있는데 파일이 없는 쪽보다 덜 위험하기 때문).
 *
 * 파일 형식은 클라이언트가 보낸 Content-Type이나 파일명이 아니라 파일 앞부분(매직 넘버)으로 판별한다.
 */
@Service
public class RiskReportImageService {

    public static final int MAX_IMAGES_PER_REPORT = 3;
    /** Supabase 버킷 제한(10MB), application.yml의 multipart 제한과 같은 값으로 맞춘다. */
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(RiskReportImageService.class);

    private final ImageStorage imageStorage;
    private final RiskReportImageRepository imageRepository;
    private final Duration viewUrlTtl;

    public RiskReportImageService(
            ImageStorage imageStorage,
            RiskReportImageRepository imageRepository,
            @Value("${storage.signed-url-ttl-minutes}") long viewUrlTtlMinutes
    ) {
        this.imageStorage = imageStorage;
        this.imageRepository = imageRepository;
        this.viewUrlTtl = Duration.ofMinutes(viewUrlTtlMinutes);
    }

    /** 검사를 통과한 사진. 형식은 파일 내용으로 판별한 값이다. */
    public record ValidatedImage(byte[] bytes, String contentType, String extension) {
    }

    /** 업로드된 파일들을 검사한다. null이나 빈 목록이면 빈 목록을 돌려준다. */
    public List<ValidatedImage> validate(List<MultipartFile> files) {
        List<ValidatedImage> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        int index = 0;
        for (MultipartFile file : files) {
            index++;
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException(index + "번째 사진 파일이 비어 있습니다.");
            }
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException(index + "번째 사진이 너무 큽니다. 사진 1장은 10MB 이하로 올려 주세요.");
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException(index + "번째 사진 파일을 읽을 수 없습니다.");
            }
            result.add(detect(bytes, index));
        }
        return result;
    }

    /**
     * 사진을 저장소에 올리고 DB 행을 만든다. 반드시 트랜잭션 안에서 호출해야 한다.
     * 트랜잭션이 커밋되지 않으면 이번에 올린 파일은 자동으로 삭제된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RiskReportImage> store(RiskReport report, List<ValidatedImage> images) {
        List<RiskReportImage> saved = new ArrayList<>();
        if (images.isEmpty()) {
            return saved;
        }

        List<String> uploadedKeys = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    uploadedKeys.forEach(RiskReportImageService.this::deleteQuietly);
                }
            }
        });

        for (ValidatedImage image : images) {
            String key = "reports/" + report.getReportId() + "/" + UUID.randomUUID() + "." + image.extension();
            imageStorage.upload(key, image.bytes(), image.contentType());
            uploadedKeys.add(key);
            saved.add(imageRepository.save(new RiskReportImage(report, key)));
        }
        return saved;
    }

    /** 현재 트랜잭션이 커밋된 뒤 저장소 파일을 지운다. 트랜잭션 밖이면 바로 지운다. */
    public void deleteFileAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    public List<RiskReportImageResponse> toResponses(List<RiskReportImage> images) {
        return images.stream()
                .map(image -> new RiskReportImageResponse(
                        image.getImageId(),
                        imageStorage.createViewUrl(image.getStoragePath(), viewUrlTtl),
                        viewUrlTtl.toSeconds(),
                        image.getCreatedAt()))
                .toList();
    }

    private void deleteQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("저장소 파일 삭제 실패 (수동 정리 필요): {}", key, e);
        }
    }

    private static ValidatedImage detect(byte[] b, int index) {
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new ValidatedImage(b, "image/jpeg", "jpg");
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return new ValidatedImage(b, "image/png", "png");
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new ValidatedImage(b, "image/webp", "webp");
        }
        throw new IllegalArgumentException(index + "번째 파일은 지원하지 않는 형식입니다. JPEG, PNG, WebP 사진만 올릴 수 있습니다.");
    }
}
