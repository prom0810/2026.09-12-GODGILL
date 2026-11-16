package com.safewalk.sensor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 워치 감지 SOS의 segment_id를 정할 때 쓰는 구간 매칭 */
class SegmentMatcherTest {

    private final SegmentMatcher matcher = new SegmentMatcher(List.of(
            new SegmentMatcher.Segment(11, 36.8000, 127.15, 36.8009, 127.15, 100),
            new SegmentMatcher.Segment(12, 36.8009, 127.15, 36.8018, 127.15, 100)));

    @Test
    void 가장_가까운_구간과_시작점부터_거리() {
        var m = matcher.match(36.80135, 127.15);
        assertThat(m.segmentId()).isEqualTo(12L);
        assertThat(m.along()).isBetween(45.0, 55.0);
        assertThat(matcher.match(36.80045, 127.1501).segmentId()).isEqualTo(11L);
    }

    @Test
    void 구간_밖의_점은_가까운_끝으로_붙는다() {
        var m = SegmentMatcher.project(new SegmentMatcher.Segment(1, 36.8, 127.15, 36.8009, 127.15, 100),
                36.7990, 127.15);
        assertThat(m.along()).isZero();
        assertThat(m.offset()).isBetween(100.0, 120.0);
    }
}
