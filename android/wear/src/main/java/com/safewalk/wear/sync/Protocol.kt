package com.safewalk.wear.sync

/**
 * 워치 <-> 폰 메시지 규약 (Wearable Data Layer, MessageClient).
 * 폰 앱의 상수와 값이 같아야 한다. 자세한 형식은 API_SPEC.md 참고.
 *
 *  워치 -> 폰
 *   PATH_TELEMETRY : 센서 배치(gzip 텍스트). 5초 분량, 헤더 JSON 1줄 + CSV 행들
 *   PATH_EVENT     : 이벤트 JSON (취소/확정 모두)  ← 서버 재학습용 라벨 포함
 *   PATH_ALERT     : 확정된 긴급 이벤트 JSON (기존 폰 WatchGateway 와의 호환용)
 *   PATH_INFO      : 워치 정보 {"deviceId"(ANDROID_ID),"model","appVersion"} ← PATH_INFO_REQUEST에 대한 응답
 *  폰 -> 워치
 *   PATH_CANCEL       : 카운트다운 취소 (폰 화면 또는 서버 2차 판정이 "오탐"일 때)
 *   PATH_INFO_REQUEST : 워치 정보 요청 (빈 본문). 폰이 워치를 등록·연결할 때 보낸다
 *   PATH_STREAM       : 센서 스트리밍 켜기/끄기 {"on":true|false}. 심박 이상 SOS 시작·종료 때 폰이 보낸다
 *
 *  capability "safewalk_watch"(res/values/wear.xml): 폰이 이 앱이 설치된 워치를 찾고 연결 끊김을 감지하는 데 쓴다.
 */
object Protocol {
    const val PATH_TELEMETRY = "/safewalk/telemetry"
    const val PATH_EVENT = "/safewalk/event"
    const val PATH_ALERT = "/safewalk/alert"
    const val PATH_CANCEL = "/safewalk/cancel"
    const val PATH_INFO_REQUEST = "/safewalk/info-request"
    const val PATH_INFO = "/safewalk/info"
    const val PATH_STREAM = "/safewalk/stream"
}
