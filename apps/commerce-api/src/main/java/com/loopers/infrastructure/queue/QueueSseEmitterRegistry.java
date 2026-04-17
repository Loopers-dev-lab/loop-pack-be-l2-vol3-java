package com.loopers.infrastructure.queue;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE Emitter 레지스트리 — 대기열 순번 실시간 Push.
 *
 * <p>Delta 기반 브로드캐스트: 매 입장 사이클마다 개별 ZRANK를 호출하지 않는다.
 * 스케줄러가 N명을 입장시키면, 연결된 모든 SSE 클라이언트에게 admittedCount를 보내고,
 * 클라이언트가 자기 position을 로컬에서 차감한다.</p>
 *
 * <p>Redis 추가 비용: O(0) (기존 ZPOPMIN만 사용).
 * SSE 전송 비용: O(K) (K = 연결된 클라이언트 수).</p>
 */
@Slf4j
@Component
public class QueueSseEmitterRegistry {

    static final int MAX_SSE_CONNECTIONS = 5_000;
    private static final long EMITTER_TIMEOUT_MS = 600_000; // 600초 (MAX_WAIT_SECONDS)

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    public QueueSseEmitterRegistry(MeterRegistry meterRegistry) {
        meterRegistry.gauge("queue.sse.connections", connectionCount);
    }

    /**
     * SSE 연결 등록 + 초기 position 전송.
     *
     * @return SseEmitter (용량 초과 시 use_polling 이벤트 후 null)
     */
    public SseEmitter register(Long memberId, long position) {
        if (connectionCount.get() >= MAX_SSE_CONNECTIONS) {
            return createOverCapacityEmitter(position);
        }

        // 중복 memberId 연결 시 기존 emitter 교체 (재연결 시나리오)
        SseEmitter existing = emitters.get(memberId);
        if (existing != null) {
            existing.complete();
            removeEmitter(memberId);
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.put(memberId, emitter);
        connectionCount.incrementAndGet();

        emitter.onCompletion(() -> removeEmitter(memberId));
        emitter.onTimeout(() -> removeEmitter(memberId));
        emitter.onError(e -> removeEmitter(memberId));

        try {
            emitter.send(SseEmitter.event()
                .name("position")
                .data(Map.of("position", position)));
        } catch (IOException e) {
            removeEmitter(memberId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 입장 처리 후 호출 — admitted 유저에게 이벤트 전송 + delta 브로드캐스트.
     */
    public void onAdmission(List<String> admittedMemberIds, int count) {
        // 1. admitted 유저에게 개별 이벤트 전송 후 emitter 닫기
        for (String memberIdStr : admittedMemberIds) {
            try {
                Long memberId = Long.parseLong(memberIdStr);
                SseEmitter emitter = emitters.get(memberId);
                if (emitter != null) {
                    emitter.send(SseEmitter.event()
                        .name("admitted")
                        .data(Map.of()));
                    emitter.complete();
                    removeEmitter(memberId);
                }
            } catch (Exception e) {
                log.debug("admitted 이벤트 전송 실패: memberId={}", memberIdStr, e);
            }
        }

        if (count <= 0) {
            return;
        }

        // 2. 나머지 대기 중 클라이언트에게 delta 브로드캐스트
        Map<String, Object> deltaData = Map.of("admittedCount", count);
        for (Map.Entry<Long, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                    .name("delta")
                    .data(deltaData));
            } catch (Exception e) {
                removeEmitter(entry.getKey());
                log.debug("delta 이벤트 전송 실패: memberId={}", entry.getKey(), e);
            }
        }
    }

    /**
     * 30초 주기 heartbeat — 빈 코멘트 전송으로 연결 유지.
     */
    public void sendHeartbeat() {
        for (Map.Entry<Long, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                removeEmitter(entry.getKey());
            }
        }
    }

    /**
     * 특정 memberId에 이벤트 전송 후 emitter 닫기 (admitted/not_in_queue 등).
     */
    public void sendAndClose(Long memberId, String eventName, Object data) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    public int getConnectionCount() {
        return connectionCount.get();
    }

    /**
     * SSE 용량 초과 시: use_polling 이벤트와 함께 즉시 닫기.
     */
    private SseEmitter createOverCapacityEmitter(long position) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            long suggestedInterval = position <= 100 ? 1000 : position <= 1000 ? 3000 : 5000;
            emitter.send(SseEmitter.event()
                .name("use_polling")
                .data(Map.of("suggestedPollIntervalMs", suggestedInterval)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void removeEmitter(Long memberId) {
        if (emitters.remove(memberId) != null) {
            connectionCount.decrementAndGet();
        }
    }
}
