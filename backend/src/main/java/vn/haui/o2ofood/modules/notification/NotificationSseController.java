package vn.haui.o2ofood.modules.notification;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.security.UserPrincipal;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationSseController {

    private final OutboxEventRepository outboxEventRepository;
    private final TimeProvider timeProvider;

    public NotificationSseController(OutboxEventRepository outboxEventRepository,
                                     TimeProvider timeProvider) {
        this.outboxEventRepository = outboxEventRepository;
        this.timeProvider = timeProvider;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(1800000L);
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("{\"status\":\"CONNECTED\",\"userId\":" + (principal != null ? principal.getId() : 0) + "}"));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @GetMapping("/outbox")
    public ResponseEntity<ApiResponse<List<OutboxEventEntity>>> listRecentEvents() {
        return ResponseEntity.ok(ApiResponse.ok(
                outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING),
                timeProvider.now()
        ));
    }
}
