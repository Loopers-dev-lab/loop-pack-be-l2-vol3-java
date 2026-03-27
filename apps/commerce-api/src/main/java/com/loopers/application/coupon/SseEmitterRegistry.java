package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 SSE 에미터 레지스트리.
 * <p>
 * 단일 인스턴스 전제로 동작한다. 다중 인스턴스 배포 시 로드밸런서에서
 * SSE 구독 요청을 동일 인스턴스로 고정(sticky session)해야 알림이 정상 전달된다.
 * 분산 알림이 필요한 경우 Redis Pub/Sub 기반으로 교체 필요.
 */
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
