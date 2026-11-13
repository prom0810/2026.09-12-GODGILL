package com.safewalk.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RouteAgeBandTest {
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    @Test
    void changesBandOnTheFourteenthBirthday() {
        assertThat(RouteAgeBand.from(LocalDate.of(2012, 11, 14), today)).isEqualTo(RouteAgeBand.UNDER_14);
        assertThat(RouteAgeBand.from(LocalDate.of(2012, 11, 13), today)).isEqualTo(RouteAgeBand.AGE_14_PLUS);
        assertThat(RouteAgeBand.from(LocalDate.of(1980, 1, 1), today)).isEqualTo(RouteAgeBand.AGE_14_PLUS);
    }

    @Test
    void handlesLeapDayWithoutChangingBandBeforeTheBirthday() {
        assertThat(RouteAgeBand.from(LocalDate.of(2012, 2, 29), LocalDate.of(2026, 2, 28)))
                .isEqualTo(RouteAgeBand.UNDER_14);
        assertThat(RouteAgeBand.from(LocalDate.of(2012, 2, 29), LocalDate.of(2026, 3, 1)))
                .isEqualTo(RouteAgeBand.AGE_14_PLUS);
    }

    @Test
    void rejectsUnknownFutureAndUnsupportedDates() {
        for (LocalDate invalid : new LocalDate[]{null, today.plusDays(1), LocalDate.of(1899, 12, 31)}) {
            assertThatThrownBy(() -> RouteAgeBand.from(invalid, today)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
