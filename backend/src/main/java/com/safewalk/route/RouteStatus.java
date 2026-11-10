package com.safewalk.route;

/**
 * 경로 안내 상태 (route.status).
 *
 * - IN_PROGRESS: 안내 중. 사용자당 1개만 유지한다 (새 안내를 시작하면 기존 것은 CANCELLED)
 * - COMPLETED: 도착. completed_at이 함께 기록된다 (DB 제약 ck_route_completed)
 * - CANCELLED: 안내 취소, 새 안내로 대체, 또는 오래 방치되어 자동 취소
 */
public enum RouteStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
