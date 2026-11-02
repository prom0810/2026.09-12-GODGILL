package com.safewalk.emergency;

import java.time.Instant;
import java.util.Optional;

/**
 * 진행 중인 SOS의 최신 위치 보관소. 위치 이력은 DB에 저장하지 않고 사건마다 마지막 1개만 둔다.
 *
 * 지금 구현은 서버 메모리({@link InMemoryLocationStore})라서 서버가 재시작되면 비워진다.
 * 폰이 5초마다 다시 보내므로 곧 복구된다. 서버를 여러 대로 나누면(MSA) Redis 구현으로 바꾼다.
 */
public interface LocationStore {

    /** 받은 위치 저장. 이미 가진 위치보다 측정 시각이 오래됐으면 버리고 false (네트워크 지연으로 순서가 뒤바뀐 경우) */
    boolean update(Long emergencyId, LocationPoint point, Instant receivedAt);

    Optional<LocationPoint> latest(Long emergencyId);

    /** 서버가 마지막으로 위치를 받은 시각 (위치 끊김 판단용) */
    Optional<Instant> lastReceivedAt(Long emergencyId);

    /** 사건 종료 시 삭제 */
    void remove(Long emergencyId);
}
