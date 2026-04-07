package com.loopers.application.ranking;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DB 매트릭 기준 Redis 랭킹 보정을 주기 실행한다.
 */
@Component
public class RankingReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RankingReconciliationScheduler.class);

    private final RankingReconciliationService reconciliationService;
    private final RankingReconciliationProperties properties;
    private final MeterRegistry meterRegistry;

    public RankingReconciliationScheduler(
            RankingReconciliationService reconciliationService,
            RankingReconciliationProperties properties,
            MeterRegistry meterRegistry) {
        this.reconciliationService = reconciliationService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 랭킹 보정 배치를 실행한다.
     * @param cron Spring cron 표현식
     * @param zone 스케줄 타임존
     */
    @Scheduled(cron = "${collector.ranking-reconciliation.cron:0 0 2 * * ?}", zone = "${collector.ranking-reconciliation.zone:Asia/Seoul}")
    public void reconcile() {
        if (!properties.enabled()) {
            return;
        }
        try {
            reconciliationService.reconcileAll();
            // 성공 카운터를 증가한다.
            meterRegistry.counter("kafka.collector.ranking.reconciliation.runs", "result", "success").increment();
        } catch (RuntimeException ex) {
            log.error("ranking reconciliation batch failed", ex);
            // 실패 카운터를 증가한다.
            meterRegistry.counter("kafka.collector.ranking.reconciliation.runs", "result", "failure").increment();
            throw ex;
        }
    }
}
