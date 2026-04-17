package com.loopers.batch.ranking.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 랭킹 MV 배치 성공 시각·스냅샷 노후화(초) 게이지. 로드맵 3.7.
 */
@Component
public class RankingBatchJobMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> lastSuccessEpochSeconds = new ConcurrentHashMap<>();

    public RankingBatchJobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Job이 COMPLETED일 때 period·periodKey별 마지막 성공 시각(epoch 초)을 갱신한다.
     *
     * @param period    WEEKLY / MONTHLY
     * @param periodKey 주간·월간 키
     */
    public void recordSuccess(String period, String periodKey) {
        String key = mapKey(period, periodKey);
        AtomicLong holder = lastSuccessEpochSeconds.computeIfAbsent(key, k -> registerGauges(period, periodKey));
        holder.set(Instant.now().getEpochSecond());
    }

    /**
     * 랭킹 배치 성공 시각·스냅샷 노후화(초) 게이지를 등록한다.
     *
     * @param period 기간
     * @param periodKey 기간 키
     * @return 랭킹 배치 성공 시각·스냅샷 노후화(초) 게이지
     */
    private AtomicLong registerGauges(String period, String periodKey) {
        AtomicLong holder = new AtomicLong(0L);
        Tags tags = Tags.of("period", period, "period_key", periodKey);
        Gauge.builder("batch.rank.job.last.success.epoch", holder, h -> (double) h.get())
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder("batch.rank.snapshot.stale.seconds", holder, RankingBatchJobMetrics::staleSeconds)
                .tags(tags)
                .register(meterRegistry);
        return holder;
    }

    /**
     * 랭킹 배치 성공 시각·스냅샷 노후화(초) 게이지를 계산한다.
     * @param lastSuccessEpoch
     * @return
     */
    private static double staleSeconds(AtomicLong lastSuccessEpoch) {
        long t = lastSuccessEpoch.get();
        if (t <= 0L) {
            return -1d;
        }
        return (double) (Instant.now().getEpochSecond() - t);
    }

    private static String mapKey(String period, String periodKey) {
        return period + "|" + periodKey;
    }
}
