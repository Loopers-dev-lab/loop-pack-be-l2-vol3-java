package com.loopers.application.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 에미터 레지스트리.
 *
 * 스케줄러가 입장 허가 후 → registry.notifyAdmitted(userId) → SSE push.
 * Controller와 QueueService가 공유하는 Spring Bean.
 *
 * ConcurrentHashMap: 멀티스레드 환경 (스케줄러 스레드 + 요청 스레드)에서 안전.
 * key: userId (String) — 스케줄러는 userId를 알고 있음 (admitBatch 결과).
 */
@Slf4j
@Component
public class QueueSseRegistry {

    // userId → SseEmitter
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SSE 에미터 등록.
     * onCompletion/onTimeout/onError: 레지스트리에서 자동 제거.
     */
    public void register(String userId, SseEmitter emitter) {
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));
    }

    /**
     * 입장 허가 이벤트 push.
     * 에미터가 없으면 no-op (폴링 방식 사용 중이거나 이미 연결 끊김).
     */
    public void notifyAdmitted(String userId) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                .name("queue-admitted")
                .data("admitted"));
            // 입장 허가 후 에미터 완료 — 클라이언트가 재연결할 이유 없음
            emitter.complete();
        } catch (IOException e) {
            log.debug("[SSE] Failed to send admitted event to userId={}. Removing emitter.", userId);
        } finally {
            emitters.remove(userId);
        }
    }

    /**
     * 에미터 명시적 제거.
     */
    public void remove(String userId) {
        emitters.remove(userId);
    }
}
