package com.safewalk.sensor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * 워치 TelemetryStreamer 배치(폰이 그대로 전달)에서 심박만 꺼낸다. 가속도·자이로는 읽지 않는다.
 *
 * 형식: gzip( 헤더 JSON 1줄 + "\n" + CSV 행들 )
 *   헤더: {"v":1,"deviceId","deviceType","seq","startMs","fs","cols":"dt,ax,ay,az,gx,gy,gz,bpm",...}
 *   행  : dt_ms,ax,ay,az,gx,gy,gz,bpm  (bpm은 없으면 빈칸)
 * 형식이 틀리면 IllegalArgumentException(400). 심박이 없는 배치는 firstBpm·lastBpm이 null.
 */
public final class TelemetryParser {

    static final int MAX_ROWS = 5_000;
    static final int MIN_BPM = 20;
    static final int MAX_BPM = 250;

    /** lastBpm = 배치에서 시각이 가장 늦은 심박, lastBpmAtMs = 그 시각(epoch ms) */
    public record Parsed(String deviceId, Integer firstBpm, Long firstBpmAtMs, Integer lastBpm, Long lastBpmAtMs) {
    }

    private TelemetryParser() {
    }

    public static Parsed parse(byte[] gzipBody, int maxUncompressedBytes, ObjectMapper mapper) {
        if (gzipBody == null || gzipBody.length == 0) {
            throw new IllegalArgumentException("센서 배치 본문이 비어 있습니다.");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new LimitedInputStream(new GZIPInputStream(new ByteArrayInputStream(gzipBody)), maxUncompressedBytes),
                StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("센서 배치 헤더가 없습니다.");
            }
            JsonNode header = mapper.readTree(headerLine);
            String deviceId = header.path("deviceId").asText("");
            if (deviceId.isBlank() || !header.hasNonNull("startMs")) {
                throw new IllegalArgumentException("센서 배치 헤더에 deviceId·startMs가 필요합니다.");
            }
            long startMs = header.get("startMs").asLong();
            String[] cols = header.path("cols").asText("dt,ax,ay,az,gx,gy,gz,bpm").split(",");
            int dtIdx = index(cols, "dt");
            int bpmIdx = index(cols, "bpm");
            if (dtIdx < 0) {
                throw new IllegalArgumentException("센서 배치 cols에 dt 열이 없습니다.");
            }

            Integer firstBpm = null, lastBpm = null;
            Long firstAt = null, lastAt = null;
            int rows = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (++rows > MAX_ROWS) {
                    throw new IllegalArgumentException("센서 배치 행이 너무 많습니다.");
                }
                if (bpmIdx < 0) {
                    continue;
                }
                String[] c = line.split(",", -1);
                try {
                    if (bpmIdx >= c.length || c[bpmIdx].isBlank()) {
                        continue;
                    }
                    long at = startMs + Long.parseLong(c[dtIdx].trim());
                    int bpm = (int) Math.round(Double.parseDouble(c[bpmIdx].trim()));
                    if (bpm < MIN_BPM || bpm > MAX_BPM) {
                        continue;
                    }
                    if (firstAt == null || at < firstAt) {
                        firstAt = at;
                        firstBpm = bpm;
                    }
                    if (lastAt == null || at >= lastAt) {
                        lastAt = at;
                        lastBpm = bpm;
                    }
                } catch (RuntimeException ignored) {
                    // 잘린 행·숫자 아닌 값은 건너뛴다
                }
            }
            if (rows == 0) {
                throw new IllegalArgumentException("센서 배치에 샘플이 없습니다.");
            }
            return new Parsed(deviceId, firstBpm, firstAt, lastBpm, lastAt);
        } catch (IOException e) {
            throw new IllegalArgumentException("센서 배치를 읽을 수 없습니다. (gzip 형식 확인)");
        }
    }

    private static int index(String[] cols, String name) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** 압축 폭탄 방지: 풀린 크기가 한도를 넘으면 중단 */
    private static final class LimitedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                throw new IllegalArgumentException("센서 배치가 너무 큽니다.");
            }
            int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (remaining <= 0) {
                throw new IllegalArgumentException("센서 배치가 너무 큽니다.");
            }
            int n = in.read(buf, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
