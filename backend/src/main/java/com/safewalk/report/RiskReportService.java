package com.safewalk.report;

import com.safewalk.report.RiskReportImageService.ValidatedImage;
import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 위험 제보 작성·조회와 제보 사진 추가·삭제.
 *
 * 규칙:
 *  - 제보 1건당 사진은 최대 {@link RiskReportImageService#MAX_IMAGES_PER_REPORT}장 (400)
 *  - 사진 추가·삭제는 본인 제보이면서 아직 처리 전(PENDING)일 때만 가능 (400)
 *  - 다른 사용자의 제보는 없는 것과 같이 404로 응답
 *
 * 승인/반려(관리자)와 제보 목록 조회는 아직 없다.
 */
@Service
public class RiskReportService {

    private static final int ADDRESS_MAX_LENGTH = 255;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int MAX_PAGE_SIZE = 50;

    private final RiskReportRepository reportRepository;
    private final RiskReportImageRepository imageRepository;
    private final RiskReportImageService imageService;
    private final UserService userService;

    public RiskReportService(
            RiskReportRepository reportRepository,
            RiskReportImageRepository imageRepository,
            RiskReportImageService imageService,
            UserService userService
    ) {
        this.reportRepository = reportRepository;
        this.imageRepository = imageRepository;
        this.imageService = imageService;
        this.userService = userService;
    }

    /** 제보 작성 요청 값 (multipart 폼 필드) */
    public record CreateCommand(
            String riskType,
            Double lat,
            Double lng,
            String address,
            String description,
            boolean anonymous
    ) {
    }

    /** POST /api/risk-reports — 제보 작성 (사진 0~3장 함께) */
    @Transactional
    public RiskReportResponse create(Long userId, CreateCommand command, List<MultipartFile> files) {
        User user = userService.getActiveUser(userId);

        RiskType riskType = RiskType.from(command.riskType());
        validateCoordinate(command.lat(), command.lng());
        String address = trimToNull(command.address(), ADDRESS_MAX_LENGTH, "주소");
        String description = trimToNull(command.description(), DESCRIPTION_MAX_LENGTH, "설명");

        // 사진 검사를 먼저 끝내서, 문제가 있으면 DB와 저장소 모두 건드리지 않는다.
        List<ValidatedImage> images = imageService.validate(files);
        checkImageCount(0, images.size());

        RiskReport report = reportRepository.save(new RiskReport(
                user, riskType, command.lat(), command.lng(), address, description, command.anonymous()));
        List<RiskReportImage> saved = imageService.store(report, images);

        return RiskReportResponse.of(report, userId, imageService.toResponses(saved));
    }

    /** GET /api/risk-reports — 반려되지 않은 제보를 최신순으로 조회한다. */
    @Transactional(readOnly = true)
    public RiskReportPageResponse getReports(Long userId, int page, int size) {
        userService.getActiveUser(userId);
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        Slice<RiskReport> reports = reportRepository.findByStatusNotOrderByCreatedAtDescReportIdDesc(
                ReportStatus.REJECTED,
                PageRequest.of(page, size));
        List<RiskReportResponse> items = reports.getContent().stream()
                .map(report -> toResponse(report, userId))
                .toList();
        return new RiskReportPageResponse(items, page, size, reports.hasNext());
    }

    /** GET /api/risk-reports/{reportId} — 다른 사용자의 공개 제보도 조회한다. */
    @Transactional(readOnly = true)
    public RiskReportResponse getReport(Long userId, Long reportId) {
        userService.getActiveUser(userId);
        RiskReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RiskReportNotFoundException("제보를 찾을 수 없습니다."));
        boolean mine = report.getUser().getUserId().equals(userId);
        if (report.getStatus() == ReportStatus.REJECTED && !mine) {
            throw new RiskReportNotFoundException("제보를 찾을 수 없습니다.");
        }
        return toResponse(report, userId);
    }

    /** POST /api/risk-reports/{reportId}/images — 본인 제보에 사진 추가 */
    @Transactional
    public RiskReportResponse addImages(Long userId, Long reportId, List<MultipartFile> files) {
        userService.getActiveUser(userId);
        List<ValidatedImage> images = imageService.validate(files);
        if (images.isEmpty()) {
            throw new IllegalArgumentException("추가할 사진(images)을 1장 이상 올려 주세요.");
        }

        RiskReport report = getEditableReport(userId, reportId);
        checkImageCount(imageRepository.countByReport_ReportId(reportId), images.size());

        imageService.store(report, images);
        report.touch();
        return toResponse(report, userId);
    }

    /** DELETE /api/risk-reports/{reportId}/images/{imageId} — 본인 제보의 사진 1장 삭제 */
    @Transactional
    public RiskReportResponse deleteImage(Long userId, Long reportId, Long imageId) {
        userService.getActiveUser(userId);
        RiskReport report = getEditableReport(userId, reportId);

        RiskReportImage image = imageRepository.findByImageIdAndReport_ReportId(imageId, reportId)
                .orElseThrow(() -> new RiskReportNotFoundException("제보 사진을 찾을 수 없습니다."));

        imageRepository.delete(image);
        imageService.deleteFileAfterCommit(image.getStoragePath());
        report.touch();
        return toResponse(report, userId);
    }

    private RiskReport getEditableReport(Long userId, Long reportId) {
        RiskReport report = reportRepository.findOwnedForUpdate(reportId, userId)
                .orElseThrow(() -> new RiskReportNotFoundException("제보를 찾을 수 없습니다."));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 제보는 사진을 변경할 수 없습니다.");
        }
        return report;
    }

    private RiskReportResponse toResponse(RiskReport report, Long viewerId) {
        List<RiskReportImage> images = imageRepository.findByReport_ReportIdOrderByImageIdAsc(report.getReportId());
        return RiskReportResponse.of(report, viewerId, imageService.toResponses(images));
    }

    private static void checkImageCount(long existing, int adding) {
        int max = RiskReportImageService.MAX_IMAGES_PER_REPORT;
        if (existing + adding > max) {
            throw new IllegalArgumentException(
                    "제보 사진은 최대 " + max + "장까지 올릴 수 있습니다. (현재 " + existing + "장)");
        }
    }

    private static void validateCoordinate(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("위치(lat, lng)를 입력해 주세요.");
        }
        if (lat.isNaN() || lat < -90 || lat > 90 || lng.isNaN() || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("위치 값이 올바르지 않습니다. (lat: -90~90, lng: -180~180)");
        }
    }

    private static String trimToNull(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return trimmed;
    }
}
