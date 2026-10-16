package com.safewalk.wear.sync

/**
 * 워치 <-> 폰 메시지 규약 (Wearable Data Layer, MessageClient).
 * 폰 앱의 상수와 값이 같아야 한다. 자세한 형식은 API_SPEC.md 참고.
 *
 *  워치 -> 폰
 *   PATH_TELEMETRY : 센서 배치(gzip 텍스트). 5초 분량, 헤더 JSON 1줄 + CSV 행들
 *   PATH_EVENT     : 이벤트 JSON (취소/확정 모두)  ← 서버 재학습용 라벨 포함
 *   PATH_ALERT     : 확정된 긴급 이벤트 JSON (기존 폰 WatchGateway 와의 호환용)
 *  폰 -> 워치
 *   PATH_CANCEL    : 카운트다운 취소 (폰 화면 또는 서버 2차 판정이 "오탐"일 때)
 */
object Protocol {
    const val PATH_TELEMETRY = "/safewalk/telemetry"
    const val PATH_EVENT = "/safewalk/event"
    const val PATH_ALERT = "/safewalk/alert"
    const val PATH_CANCEL = "/safewalk/cancel"
}
