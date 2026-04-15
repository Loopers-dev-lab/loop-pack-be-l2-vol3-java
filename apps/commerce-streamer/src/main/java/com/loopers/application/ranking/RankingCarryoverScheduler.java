package com.loopers.application.ranking;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 자정 직후 전일 랭킹 carryover를 실행한다.
 */
@Component
public class RankingCarryoverScheduler {

    private static final Logger log = LoggerFactory.getLogger(RankingCarryoverScheduler.class);

    private final RankingCarryoverService carryoverService;
    private final RankingCarryoverProperties properties;
    private final MeterRegistry meterRegistry;

    public RankingCarryoverScheduler(
            RankingCarryoverService carryoverService,
            RankingCarryoverProperties properties,
            MeterRegistry meterRegistry) {
        this.carryoverService = carryoverService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 자정 직후 전일 랭킹 carryover를 실행한다.
     * 목적: 날짜 경계(자정) 직후 당일 키가 비어 순위가 급변(0 → N)하는 UX를 완화한다.
     * "전일 Top N을 낮은 가중치로 복사"하고, 이후 실시간/보정 경로의 매트릭 기반 ZADD로 자연스럽게 수렴한다.
     *
     * @see RankingCarryoverProperties#cron
     * @see RankingCarryoverProperties#zone
     */
    @Scheduled(cron = "${collector.ranking-carryover.cron:0 0 0 * * ?}", zone = "${collector.ranking-carryover.zone:Asia/Seoul}")
    public void carryover() {
        if (!properties.enabled()) {
            return;
        }
        try {
            carryoverService.carryoverToday(properties);
            // 성공 카운터 증가
            meterRegistry.counter("kafka.collector.ranking.carryover.runs", "result", "success").increment();
        } catch (RuntimeException ex) {
            log.error("ranking carryover failed", ex);
            // 실패 카운터 증가
            meterRegistry.counter("kafka.collector.ranking.carryover.runs", "result", "failure").increment();
            throw ex;
        }
    }
}

