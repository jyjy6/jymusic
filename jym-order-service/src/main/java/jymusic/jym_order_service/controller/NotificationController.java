package jymusic.jym_order_service.controller;

import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.notification.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new GlobalException("로그인이 필요합니다.", "ERR_UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        return registry.register(memberId);
    }

    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public SseEmitter subscribeAdmin() {
        return registry.register(SseEmitterRegistry.ADMIN_KEY);
    }
}
