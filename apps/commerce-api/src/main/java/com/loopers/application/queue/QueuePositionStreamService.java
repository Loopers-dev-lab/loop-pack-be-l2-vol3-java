package com.loopers.application.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.metrics.QueueInfrastructureMetrics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code GET /queue/position/stream}용: 한 유저에 대해 대기열 순번을 SSE로 반복 푸시한다.
 * <p>
 * 각 턴마다 {@link QueueFacade#getQueuePosition}으로 스냅샷을 읽고, JSON 이벤트로 보낸 뒤
 * {@link QueuePositionInfo#suggestedPollIntervalMs()}만큼 대기한다(최소 1초). 대기열에 없어지면
 * {@code not-in-queue} 이벤트를 보내고 스트림을 종료한다.
 * <p>
 * 연결은 {@link SseEmitter} 타임아웃(기본 5분)까지 유지되며,
 * 완료·타임아웃·오류 시 백그라운드 스레드를 정리한다.
 * <p>
 * 동시 연결 수는 {@link QueuePositionSseConcurrencyLimiter}로 제한되며, 한도 초과 시 {@link CoreException}({@link ErrorType#TOO_MANY_REQUESTS})을 던진다.
 */
@Service
public class QueuePositionStreamService {

    /** 클라이언트가 붙을 수 있는 최대 시간(5분). 그 전에 끊기면 onCompletion/onError로 정리된다. */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private static final String CONCURRENCY_MESSAGE =
            "동시 순번 스트림 연결 수가 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.";

    private final QueueFacade queueFacade;
    private final ObjectMapper objectMapper;
    private final QueuePositionSseConcurrencyLimiter sseConcurrencyLimiter;
    private final QueueInfrastructureMetrics queueInfrastructureMetrics;

    public QueuePositionStreamService(
            QueueFacade queueFacade,
            ObjectMapper objectMapper,
            QueuePositionSseConcurrencyLimiter sseConcurrencyLimiter,
            QueueInfrastructureMetrics queueInfrastructureMetrics
    ) {
        this.queueFacade = queueFacade;
        this.objectMapper = objectMapper;
        this.sseConcurrencyLimiter = sseConcurrencyLimiter;
        this.queueInfrastructureMetrics = queueInfrastructureMetrics;
    }

    /**
     * 해당 {@code userId} 전용 {@link SseEmitter}를 만들고, 별도 스레드에서 순번 루프를 돌린다.
     * 호출은 컨트롤러에서 인증 직후 한 번만 하면 된다.
     */
    public SseEmitter subscribe(Long userId) {
        if (!sseConcurrencyLimiter.tryAcquire()) {
            queueInfrastructureMetrics.recordSseConcurrencyRejected();
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS, CONCURRENCY_MESSAGE);
        }
        AtomicBoolean permitReleased = new AtomicBoolean(false);
        Runnable releasePermitOnce =
                () -> {
                    if (permitReleased.compareAndSet(false, true)) {
                        sseConcurrencyLimiter.release();
                    }
                };

        final SseEmitter emitter;
        final ExecutorService executor;
        final Future<?> future;
        try {
            emitter = new SseEmitter(SSE_TIMEOUT_MS);
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "queue-position-sse-" + userId);
                t.setDaemon(true);
                return t;
            });
            future = executor.submit(() -> runLoop(emitter, userId));
        } catch (Throwable t) {
            releasePermitOnce.run();
            throw t;
        }
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (!cleanedUp.compareAndSet(false, true)) {
                return;
            }
            future.cancel(true);
            executor.shutdownNow();
            releasePermitOnce.run();
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
                String json = toJson(QueuePositionSseDto.from(info));
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

    /** SSE 본문용 JSON 문자열. 스키마는 {@link QueuePositionSseDto}와 동일. */
    private String toJson(QueuePositionSseDto dto) throws JsonProcessingException {
        return objectMapper.writeValueAsString(dto);
    }
}
