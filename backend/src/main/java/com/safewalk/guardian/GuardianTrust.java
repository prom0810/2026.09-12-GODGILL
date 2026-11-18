package com.safewalk.guardian;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * "믿을 수 있는 보호자" 판단과, 그에 따른 삭제·해제 제한과 대표 승계 규칙.
 *
 * 믿을 수 있는 보호자 = 보호자 본인이 동의한 보호자
 *  - 앱으로 연결됨(CONNECTED): 앱에서 초대를 수락함
 *  - 인증번호로 확인됨(smsVerified): 받은 인증번호를 사용자에게 알려 줌
 * 이름·번호만 등록된 보호자는 아무 번호나 넣을 수 있어서 믿을 수 있는 보호자로 보지 않는다.
 */
final class GuardianTrust {

    private GuardianTrust() {
    }

    static boolean isTrusted(Guardian guardian) {
        return guardian.getRegistrationStatus() == RegistrationStatus.CONNECTED || guardian.isSmsVerified();
    }

    /**
     * 미성년자가 믿을 수 있는 보호자를 모두 잃게 되는 변경인지.
     *
     * 지금 믿을 수 있는 보호자가 1명 이상인데, 변경 후 0명이 되면 true.
     * 원래 0명이었다면(기능 도입 전 데이터 등) 이 규칙으로 막지 않는다 — 삭제해도 잃는 것이 없기 때문.
     * 이 규칙이 없으면 아무 번호나 보호자로 추가한 뒤 진짜 보호자를 지우는 식으로 보호자를 바꿔치기할 수 있다.
     *
     * @param active             삭제되지 않은 보호자 전체 (target 포함)
     * @param target             삭제·해제하려는 보호자
     * @param targetTrustedAfter 변경 후에도 target이 믿을 수 있는 보호자로 남는지 (삭제면 false)
     */
    static boolean losesLastTrusted(List<Guardian> active, Guardian target, boolean targetTrustedAfter) {
        long before = active.stream().filter(GuardianTrust::isTrusted).count();
        long after = before - (isTrusted(target) ? 1 : 0) + (targetTrustedAfter ? 1 : 0);
        return before > 0 && after == 0;
    }

    /**
     * 대표 보호자가 삭제됐을 때 새 대표를 고른다.
     *
     * 대표 보호자는 SOS 때 "앱이 없는 보호자에게 보내는 문자"의 대상이다 (앱으로 연결된 보호자는 대표가 아니어도 모두 푸시를 받음).
     * 그래서 문자를 실제로 받을 수 있는 사람을 먼저 고른다.
     *  1. 앱 미연결 + 인증번호 확인됨 → 문자를 받는다 (연결된 보호자는 따로 푸시를 받으므로 알림 범위가 가장 넓다)
     *  2. 앱으로 연결됨 → 푸시를 받는다
     *  3. 그 외 (미확인) → 알림을 받지 못하지만, 대표가 아예 없는 상태보다는 낫다
     * 같은 순위 안에서는 먼저 등록된 보호자.
     */
    static Optional<Guardian> pickPrimarySuccessor(List<Guardian> remaining) {
        return remaining.stream()
                .min(Comparator.comparingInt(GuardianTrust::successorRank)
                        .thenComparing(Guardian::getGuardianId));
    }

    private static int successorRank(Guardian guardian) {
        boolean connected = guardian.getRegistrationStatus() == RegistrationStatus.CONNECTED;
        if (!connected && guardian.isSmsVerified()) {
            return 0;
        }
        return connected ? 1 : 2;
    }
}
