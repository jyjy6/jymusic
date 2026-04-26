package jymusic.jym_order_service.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SseEmitterRegistry {

    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L;
    public static final String ADMIN_KEY = "ROLE_ADMIN";

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String key) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remove = () -> {
            List<SseEmitter> list = emitters.get(key);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(key);
                }
            }
        };

        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());

        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void sendToMember(Long memberId, String eventName, Object data) {
        send(String.valueOf(memberId), eventName, data);
    }

    public void sendToAdmins(String eventName, Object data) {
        send(ADMIN_KEY, eventName, data);
    }

    private void send(String key, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        emitters.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("PING").data(""));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }));
    }
}
