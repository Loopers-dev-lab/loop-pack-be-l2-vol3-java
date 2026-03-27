package com.loopers.batch;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.infrastructure.monitoring.OutboxMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 이벤트 릴레이 스케줄러.
 *
 * <p>5초 간격으로 PENDING 상태의 Outbox 이벤트를 폴링하여
 * {@link OutboxEventProcessor}를 통해 Kafka로 발행한다.</p>
 *
 * <p>실행 시간 상한(60초)을 두어 단일 폴링이 다음 주기를 침범하지 않도록 한다.</p>
 *
 * <p>폴링 쿼리 자체가 실패하면 연속 에러 횟수에 비례하여 지수 백오프(5→10→20→40→60초)를
 * 적용하여 DB/앱 자원 소진을 방지한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventRelay {

    private static final int BATCH_SIZE = 100;
    private static final Duration MAX_EXECUTION_TIME = Duration.ofSeconds(60);
    private static final long BASE_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;

    private final OutboxEventRepository outboxRepository;
    private final OutboxEventProcessor outboxEventProcessor;
    private final OutboxMetrics outboxMetrics;

    private int consecutiveErrors = 0;
    private Instant backoffUntil = Instant.MIN;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        if (Instant.now().isBefore(backoffUntil)) {
            log.debug("[OutboxRelay] 백오프 대기 중, 다음 시도: {}", backoffUntil);
            return;
        }

        Timer.Sample sample = outboxMetrics.startRelayTimer();
        List<OutboxEventModel> events;
        try {
            events = outboxRepository.findPendingEvents(BATCH_SIZE);
        } catch (Exception e) {
            consecutiveErrors++;
            long backoffMs = Math.min(BASE_BACKOFF_MS * (1L << consecutiveErrors), MAX_BACKOFF_MS);
            backoffUntil = Instant.now().plusMillis(backoffMs);
            outboxMetrics.stopRelayTimer(sample);
            log.error("[OutboxRelay] 폴링 쿼리 실패 (연속 {}회), {}ms 백오프 적용",
                consecutiveErrors, backoffMs, e);
            return;
        }

        consecutiveErrors = 0;
        backoffUntil = Instant.MIN;

        if (events.isEmpty()) {
            outboxMetrics.stopRelayTimer(sample);
            return;
        }

        Instant deadline = Instant.now().plus(MAX_EXECUTION_TIME);
        int success = 0, failed = 0;

        for (OutboxEventModel event : events) {
            if (Instant.now().isAfter(deadline)) {
                log.warn("[OutboxRelay] 실행 시간 상한 초과, 잔여 {}건은 다음 폴링에서 처리",
                    events.size() - success - failed);
                break;
            }
            try {
                boolean ok = outboxEventProcessor.publishAndMark(event);
                if (ok) {
                    success++;
                    outboxMetrics.recordPublishSuccess();
                } else {
                    failed++;
                    outboxMetrics.recordPublishFail();
                }
            } catch (Exception e) {
                failed++;
                outboxMetrics.recordPublishFail();
                log.error("[OutboxRelay] eventId={} 처리 중 예외", event.getEventId(), e);
            }
        }

        outboxMetrics.stopRelayTimer(sample);

        if (success > 0 || failed > 0) {
            log.info("[OutboxRelay] 성공={}, 실패={}, 전체={}", success, failed, events.size());
        }
    }
}
