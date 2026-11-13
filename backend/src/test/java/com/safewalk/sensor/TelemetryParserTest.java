package com.safewalk.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class TelemetryParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HEADER = "{\"v\":1,\"deviceId\":\"watch-1\",\"deviceType\":\"SM-R\",\"seq\":7,"
            + "\"startMs\":1000000,\"fs\":50,\"cols\":\"dt,ax,ay,az,gx,gy,gz,bpm\"}";

    static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    @Test
    void 배치에서_처음과_마지막_심박만_꺼낸다() throws IOException {
        String body = HEADER + "\n"
                + "0,0,0,9.8,0,0,0,\n"             // 심박 없음
                + "20,0,0,9.8,0,0,0,121\n"         // 첫 심박
                + "40,bad,row\n"                  // 건너뜀
                + "60,0,0,9.8,0,0,0,300\n"         // 범위 밖 → 무시
                + "80,0,0,9.8,0,0,0,133.6\n";      // 마지막 심박 (반올림 134)
        TelemetryParser.Parsed p = TelemetryParser.parse(gzip(body), 1 << 20, MAPPER);
        assertThat(p.deviceId()).isEqualTo("watch-1");
        assertThat(p.firstBpm()).isEqualTo(121);
        assertThat(p.firstBpmAtMs()).isEqualTo(1_000_020L);
        assertThat(p.lastBpm()).isEqualTo(134);
        assertThat(p.lastBpmAtMs()).isEqualTo(1_000_080L);
    }

    @Test
    void 심박이_없는_배치는_null() throws IOException {
        TelemetryParser.Parsed p = TelemetryParser.parse(gzip(HEADER + "\n0,0,0,9.8,0,0,0,\n"), 1 << 20, MAPPER);
        assertThat(p.lastBpm()).isNull();
        assertThat(p.firstBpm()).isNull();
    }

    @Test
    void 잘못된_배치는_거절한다() throws IOException {
        assertThatThrownBy(() -> TelemetryParser.parse(new byte[]{1, 2, 3}, 1 << 20, MAPPER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TelemetryParser.parse(gzip("{\"seq\":1}\n0,0,0,0,0,0,0,\n"), 1 << 20, MAPPER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("deviceId");
        assertThatThrownBy(() -> TelemetryParser.parse(gzip(HEADER + "\n"), 1 << 20, MAPPER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("샘플");
    }

    @Test
    void 풀린_크기가_한도를_넘으면_거절한다() throws IOException {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < 2000; i++) {
            sb.append(i).append(",0,0,9.8,0,0,0,80\n");
        }
        assertThatThrownBy(() -> TelemetryParser.parse(gzip(sb.toString()), 4096, MAPPER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("너무 큽니다");
    }
}
