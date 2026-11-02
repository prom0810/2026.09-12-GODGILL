package com.safewalk.report;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 위험 제보 API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 *
 * 사진을 함께 보내므로 작성·사진 추가는 JSON이 아니라 multipart/form-data로 요청한다.
 *  - 텍스트 필드: riskType, lat, lng, address(선택), description(선택), anonymous(선택, 기본 false)
 *  - 파일 필드: images (같은 이름으로 여러 개, 최대 3장, 1장당 10MB 이하, JPEG/PNG/WebP)
 *
 * 응답의 사진 url은 만료 시간이 있는 주소이므로 앱에 저장하지 말고 필요할 때 제보를 다시 조회한다.
 */
@RestController
@RequestMapping("/api/risk-reports")
public class RiskReportController {

    private final RiskReportService riskReportService;

    public RiskReportController(RiskReportService riskReportService) {
        this.riskReportService = riskReportService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RiskReportResponse> create(
            @LoginUser Long userId,
            @RequestParam("riskType") String riskType,
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "anonymous", defaultValue = "false") boolean anonymous,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        RiskReportService.CreateCommand command =
                new RiskReportService.CreateCommand(riskType, lat, lng, address, description, anonymous);
        return ApiResponse.ok(riskReportService.create(userId, command, images));
    }

    @GetMapping
    public ApiResponse<RiskReportPageResponse> list(
            @LoginUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(riskReportService.getReports(userId, page, size));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<RiskReportResponse> getReport(@LoginUser Long userId, @PathVariable Long reportId) {
        return ApiResponse.ok(riskReportService.getReport(userId, reportId));
    }

    @PostMapping(value = "/{reportId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RiskReportResponse> addImages(
            @LoginUser Long userId,
            @PathVariable Long reportId,
            @RequestParam("images") List<MultipartFile> images
    ) {
        return ApiResponse.ok(riskReportService.addImages(userId, reportId, images));
    }

    @DeleteMapping("/{reportId}/images/{imageId}")
    public ApiResponse<RiskReportResponse> deleteImage(
            @LoginUser Long userId,
            @PathVariable Long reportId,
            @PathVariable Long imageId
    ) {
        return ApiResponse.ok(riskReportService.deleteImage(userId, reportId, imageId));
    }
}
