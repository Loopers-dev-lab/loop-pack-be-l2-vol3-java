package com.loopers.application.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * {@code GET /queue/position/stream}용: 한 유저에 대해 대기열 순번을 SSE로 반복 푸시한다.
 * <p>
 * 각 턴마다 {@link QueueFacade#getQueuePosition}으로 스냅샷을 읽고, JSON 이벤트로 보낸 뒤
 * {@link QueuePositionInfo#suggestedPollIntervalMs()}만큼 대기한다(최소 1초). 대기열에 없어지면
 * {@code not-in-queue} 이벤트를 보내고 스트림을 종료한다.
 * <p>
 * 연결은 {@link SseEmitter} 타임아웃(기본 5분)까지 유지되며,
 * 완료·타임아웃·오류 시 백그라운드 스레드를 정리한다.
 */
@Service
public class QueuePositionStreamService {

    /** 클라이언트가 붙을 수 있는 최대 시간(5분). 그 전에 끊기면 onCompletion/onError로 정리된다. */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final QueueFacade queueFacade;
    private final ObjectMapper objectMapper;

    public QueuePositionStreamService(QueueFacade queueFacade, ObjectMapper objectMapper) {
        this.queueFacade = queueFacade;
        this.objectMapper = objectMapper;
    }

    /**
     * 해당 {@code userId} 전용 {@link SseEmitter}를 만들고, 별도 스레드에서 순번 루프를 돌린다.
     * 호출은 컨트롤러에서 인증 직후 한 번만 하면 된다.
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "queue-position-sse-" + userId);
            t.setDaemon(true);
            return t;
        });
        Future<?> future = executor.submit(() -> runLoop(emitter, userId));
        Runnable cleanup = () -> {
            future.cancel(true);
            executor.shutdownNow();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /**
     * 순번 조회 → 이벤트 전송 → 권장 간격만큼 sleep을 반복한다. 인터럽트·I/O 오류 시 emitter를 닫는다.
     */
    private void runLoop(SseEmitter emitter, Long userId) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Optional<QueuePositionInfo> opt = queueFacade.getQueuePosition(userId);
                if (opt.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("not-in-queue")
                            .data("{\"message\":\"대기열에 없습니다.\"}"));
                    emitter.complete();
                    return;
                }
                QueuePositionInfo info = opt.get();
                String json = toJson(QueuePositionSsePayload.from(info));
                emitter.send(SseEmitter.event().data(json));
                long sleepMs = Math.max(1000L, info.suggestedPollIntervalMs());
                Thread.sleep(sleepMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** SSE 본문용 JSON 문자열. 스키마는 {@link QueuePositionSsePayload}와 동일. */
    private String toJson(QueuePositionSsePayload payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }
}
