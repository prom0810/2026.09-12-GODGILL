package com.safewalk.ai.roadimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoadImageAnalysisServiceTest {

    private static final double BASE_LATITUDE = 36.811782;
    private static final double BASE_LONGITUDE = 127.141176;

    @Mock
    private RoadImageAnalysisRepository repository;

    private RoadImageAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new RoadImageAnalysisService(repository);
    }

    @Test
    void findAll_convertsEntitiesToResponsesInRepositoryOrder() {
        RoadImageAnalysis first = analysis(1L, "roadview_test_00.png", BASE_LATITUDE, BASE_LONGITUDE);
        RoadImageAnalysis second = analysis(2L, "roadview_test_01.png", BASE_LATITUDE + 0.001, BASE_LONGITUDE);
        when(repository.findAllByOrderByImageAnalysisIdAsc()).thenReturn(List.of(first, second));

        List<RoadImageAnalysisResponse> result = service.findAll();

        assertThat(result)
                .extracting(RoadImageAnalysisResponse::imageAnalysisId)
                .containsExactly(1L, 2L);
        assertThat(result)
                .extracting(RoadImageAnalysisResponse::imageName)
                .containsExactly("roadview_test_00.png", "roadview_test_01.png");
        verify(repository).findAllByOrderByImageAnalysisIdAsc();
    }

    @Test
    void findNearby_returnsOnlyResultsWithinRadiusSortedByDistance() {
        RoadImageAnalysis samePosition =
                analysis(1L, "same.png", BASE_LATITUDE, BASE_LONGITUDE);
        RoadImageAnalysis about56MetersAway =
                analysis(2L, "near.png", BASE_LATITUDE + 0.0005, BASE_LONGITUDE);
        RoadImageAnalysis about222MetersAway =
                analysis(3L, "far.png", BASE_LATITUDE + 0.002, BASE_LONGITUDE);
        when(repository.findAll()).thenReturn(List.of(about222MetersAway, about56MetersAway, samePosition));

        List<NearbyRoadImageAnalysisResponse> result =
                service.findNearby(BASE_LATITUDE, BASE_LONGITUDE, 100, 20);

        assertThat(result)
                .extracting(response -> response.analysis().imageName())
                .containsExactly("same.png", "near.png");
        assertThat(result.get(0).distanceMeters()).isCloseTo(0.0, withinMeters(0.001));
        assertThat(result.get(1).distanceMeters()).isBetween(55.0, 57.0);
        verify(repository).findAll();
    }

    @Test
    void findNearby_appliesLimitAfterSortingByDistance() {
        RoadImageAnalysis samePosition =
                analysis(1L, "same.png", BASE_LATITUDE, BASE_LONGITUDE);
        RoadImageAnalysis about56MetersAway =
                analysis(2L, "near.png", BASE_LATITUDE + 0.0005, BASE_LONGITUDE);
        RoadImageAnalysis about89MetersAway =
                analysis(3L, "third.png", BASE_LATITUDE + 0.0008, BASE_LONGITUDE);
        when(repository.findAll()).thenReturn(List.of(about89MetersAway, about56MetersAway, samePosition));

        List<NearbyRoadImageAnalysisResponse> result =
                service.findNearby(BASE_LATITUDE, BASE_LONGITUDE, 100, 2);

        assertThat(result)
                .extracting(response -> response.analysis().imageName())
                .containsExactly("same.png", "near.png");
        assertThat(result).hasSize(2);
    }

    @ParameterizedTest
    @MethodSource("invalidSearchRequests")
    void findNearby_rejectsInvalidSearchParameters(
            double latitude,
            double longitude,
            double radiusMeters,
            int limit
    ) {
        assertThatThrownBy(() -> service.findNearby(latitude, longitude, radiusMeters, limit))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    private static Stream<Arguments> invalidSearchRequests() {
        return Stream.of(
                Arguments.of(90.0001, BASE_LONGITUDE, 100.0, 20),
                Arguments.of(-90.0001, BASE_LONGITUDE, 100.0, 20),
                Arguments.of(BASE_LATITUDE, 180.0001, 100.0, 20),
                Arguments.of(BASE_LATITUDE, -180.0001, 100.0, 20),
                Arguments.of(Double.NaN, BASE_LONGITUDE, 100.0, 20),
                Arguments.of(BASE_LATITUDE, Double.POSITIVE_INFINITY, 100.0, 20),
                Arguments.of(BASE_LATITUDE, BASE_LONGITUDE, 0.0, 20),
                Arguments.of(BASE_LATITUDE, BASE_LONGITUDE, -1.0, 20),
                Arguments.of(BASE_LATITUDE, BASE_LONGITUDE, 20_000.1, 20),
                Arguments.of(BASE_LATITUDE, BASE_LONGITUDE, Double.NaN, 20),
                Arguments.of(BASE_LATITUDE, BASE_LONGITUDE, 100.0, 0),
                Arguments.of(BASE_LATITUDE, BASE_LONGITUDE, 100.0, 201)
        );
    }

    private static RoadImageAnalysis analysis(Long id, String imageName, double latitude, double longitude) {
        RoadImageAnalysis analysis = new RoadImageAnalysis();
        ReflectionTestUtils.setField(analysis, "imageAnalysisId", id);
        ReflectionTestUtils.setField(analysis, "imageName", imageName);
        ReflectionTestUtils.setField(analysis, "latitude", latitude);
        ReflectionTestUtils.setField(analysis, "longitude", longitude);
        ReflectionTestUtils.setField(analysis, "direction", "북");
        ReflectionTestUtils.setField(analysis, "riskLevel", "보통");
        ReflectionTestUtils.setField(analysis, "modelVersion", "risk-v2");
        ReflectionTestUtils.setField(analysis, "analyzedAt", OffsetDateTime.parse("2026-10-27T00:00:00Z"));
        return analysis;
    }

    private static org.assertj.core.data.Offset<Double> withinMeters(double meters) {
        return org.assertj.core.data.Offset.offset(meters);
    }
}
