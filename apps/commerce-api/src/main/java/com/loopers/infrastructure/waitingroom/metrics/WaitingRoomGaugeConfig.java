package com.loopers.infrastructure.waitingroom.metrics;

import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.ENTRY_GATE_ACTIVE;
import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.WAITING_SIZE;

import com.loopers.domain.waitingroom.EntryGate;
import com.loopers.domain.waitingroom.WaitingQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 대기열/참여열 Gauge를 애플리케이션 시작 시 한 번만 등록한다.
 * Prometheus scrape 주기마다 supplier가 호출되어 Redis에서 현재값을 조회한다.
 */
@Component
@RequiredArgsConstructor
public class WaitingRoomGaugeConfig {

    private final MeterRegistry registry;
    private final WaitingQueue waitingQueue;
    private final EntryGate entryGate;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder(WAITING_SIZE, waitingQueue::getTotalWaiting)
                .description("현재 대기열 인원")
                .register(registry);

        Gauge.builder(ENTRY_GATE_ACTIVE, entryGate::getActiveCount)
                .description("현재 참여열 인원")
                .register(registry);
    }
}
