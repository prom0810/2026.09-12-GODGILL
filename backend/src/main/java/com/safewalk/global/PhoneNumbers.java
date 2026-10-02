package com.safewalk.global;

import java.util.regex.Pattern;

/**
 * 휴대폰 번호 정규화 유틸 (사용자·보호자 공용).
 *
 * 입력 형식에 상관없이 "010-1234-5678" 형태로 통일한다.
 *  - "01012345678", "010 1234 5678", "010.1234.5678", "+82 10-1234-5678" → "010-1234-5678"
 *  - 10자리 구형 번호 "0111234567" → "011-123-4567"
 * 휴대폰 번호(010/011/016/017/018/019로 시작하는 10~11자리)가 아니면 IllegalArgumentException(400).
 */
public final class PhoneNumbers {

    private static final Pattern MOBILE = Pattern.compile("01[016789]\\d{7,8}");

    private PhoneNumbers() {
    }

    /**
     * @param raw        사용자가 입력한 전화번호
     * @param fieldLabel 오류 메시지에 쓸 항목 이름 (예: "전화번호", "보호자 전화번호")
     */
    public static String normalize(String raw, String fieldLabel) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.startsWith("82")) {
            // 국가번호(+82) 형식 → 국내 형식 (8210... → 010...)
            digits = "0" + digits.substring(2);
        }
        if (!MOBILE.matcher(digits).matches()) {
            throw new IllegalArgumentException(fieldLabel + " 형식이 올바르지 않습니다. (예: 010-1234-5678)");
        }
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
    }
}
