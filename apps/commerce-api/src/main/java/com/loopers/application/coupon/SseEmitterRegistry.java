package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String requestId) {
        SseEmitter emitter = new SseEmitter(30_000L);
        emitter.onCompletion(() -> emitters.remove(requestId));
        emitter.onTimeout(() -> emitters.remove(requestId));
        emitters.put(requestId, emitter);
        return emitter;
    }

    public Set<String> getPendingRequestIds() {
        return emitters.keySet();
    }

    public void complete(String requestId, CouponIssueStatus status) {
        SseEmitter emitter = emitters.remove(requestId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("result").data(status.name()));
            emitter.complete();
        } catch (IOException e) {
            log.warn("SSE 전송 실패: requestId={}", requestId, e);
            emitter.completeWithError(e);
        }
    }
}
