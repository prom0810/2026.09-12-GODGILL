package com.safewalk.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.safewalk.user.User;
import com.safewalk.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RiskReportServiceTest {

    @Mock
    private RiskReportRepository reportRepository;
    @Mock
    private RiskReportImageRepository imageRepository;
    @Mock
    private RiskReportImageService imageService;
    @Mock
    private UserService userService;

    private RiskReportService service;

    @BeforeEach
    void setUp() {
        service = new RiskReportService(reportRepository, imageRepository, imageService, userService);
    }

    @Test
    void communityListHidesAnonymousAuthorAndMarksOwnReport() {
        User author = user(7L, "홍길동");
        RiskReport report = new RiskReport(author, RiskType.OTHER, 37.5, 127.0,
                "서울시 종로구", "보도블록이 깨져 있습니다.", true);
        ReflectionTestUtils.setField(report, "reportId", 31L);

        when(reportRepository.findByStatusNotOrderByCreatedAtDescReportIdDesc(
                ReportStatus.REJECTED, PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of(report), PageRequest.of(0, 20), false));
        when(imageRepository.findByReport_ReportIdOrderByImageIdAsc(31L)).thenReturn(List.of());
        when(imageService.toResponses(any())).thenReturn(List.of());

        RiskReportPageResponse response = service.getReports(7L, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).authorName()).isEqualTo("익명");
        assertThat(response.items().get(0).mine()).isTrue();
        verify(userService).getActiveUser(7L);
    }

    @Test
    void communityListShowsAuthorNameForNonAnonymousReport() {
        User author = user(8L, "김안전");
        RiskReport report = new RiskReport(author, RiskType.DARK_ROAD, 37.5, 127.0,
                null, "가로등이 꺼져 있습니다.", false);
        ReflectionTestUtils.setField(report, "reportId", 32L);

        when(reportRepository.findByStatusNotOrderByCreatedAtDescReportIdDesc(
                ReportStatus.REJECTED, PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of(report), PageRequest.of(0, 20), false));
        when(imageRepository.findByReport_ReportIdOrderByImageIdAsc(32L)).thenReturn(List.of());
        when(imageService.toResponses(any())).thenReturn(List.of());

        RiskReportResponse item = service.getReports(7L, 0, 20).items().get(0);

        assertThat(item.authorName()).isEqualTo("김안전");
        assertThat(item.mine()).isFalse();
    }

    @Test
    void rejectedReportIsHiddenFromOtherUsers() {
        User author = user(8L, "김안전");
        RiskReport report = new RiskReport(author, RiskType.OTHER, 37.5, 127.0,
                null, "반려된 제보", false);
        ReflectionTestUtils.setField(report, "reportId", 33L);
        ReflectionTestUtils.setField(report, "status", ReportStatus.REJECTED);
        when(reportRepository.findById(33L)).thenReturn(java.util.Optional.of(report));

        assertThatThrownBy(() -> service.getReport(7L, 33L))
                .isInstanceOf(RiskReportNotFoundException.class);
    }

    private User user(Long id, String name) {
        User user = new User(null, name, name + "@example.com", "password", "010-0000-0000");
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }
}
