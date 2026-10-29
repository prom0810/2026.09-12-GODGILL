package com.safewalk.notification;

import com.safewalk.global.ApiResponse;
import com.safewalk.global.LoginUser;
import com.safewalk.user.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함 API. 모든 요청에 "Authorization: Bearer {토큰}" 헤더가 필요하다.
 *
 *  - GET   /api/notifications?page=0&size=20   내 알림 (최신순, size 최대 50)
 *  - GET   /api/notifications/unread-count     안 읽은 알림 수 (배지)
 *  - PATCH /api/notifications/{id}/read        읽음 처리 (다른 사람 알림이면 404)
 *  - PATCH /api/notifications/read-all         전체 읽음
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<NotificationResponses.Page> list(@LoginUser Long userId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        userService.getActiveUser(userId);
        return ApiResponse.ok(notificationService.getMyNotifications(userId, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationResponses.UnreadCount> unreadCount(@LoginUser Long userId) {
        userService.getActiveUser(userId);
        return ApiResponse.ok(notificationService.countUnread(userId));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponses.Item> markRead(@LoginUser Long userId,
                                                            @PathVariable Long notificationId) {
        userService.getActiveUser(userId);
        return ApiResponse.ok(notificationService.markRead(userId, notificationId));
    }

    @PatchMapping("/read-all")
    public ApiResponse<NotificationResponses.ReadAll> markAllRead(@LoginUser Long userId) {
        userService.getActiveUser(userId);
        return ApiResponse.ok(notificationService.markAllRead(userId));
    }
}
