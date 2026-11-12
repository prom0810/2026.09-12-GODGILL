package com.safewalk.wearable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 웨어러블 연결 설정 (application.yml의 wearable.*, .env로 바꿀 수 있다).
 *
 *  - connectionStaleHours: 다른 계정에서 연결 중인 워치라도, 그 연결 상태가 이 시간 넘게 바뀌지 않았고
 *    그 계정이 경로 안내 중이 아니면 갱신이 멈춘 연결로 보고 해제한 뒤 새 계정에 연결한다 (기본 24시간).
 *    앱은 실행할 때와 경로 안내를 시작할 때 연결을 다시 확인해 시각을 갱신한다.
 */
@Component
public class WearableProperties {

    private final long connectionStaleHours;

    public WearableProperties(@Value("${wearable.connection-stale-hours:24}") long connectionStaleHours) {
        if (connectionStaleHours < 1) {
            throw new IllegalStateException("wearable.connection-stale-hours는 1 이상이어야 합니다.");
        }
        this.connectionStaleHours = connectionStaleHours;
    }

    public long connectionStaleHours() {
        return connectionStaleHours;
    }
}
