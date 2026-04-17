package com.loopers.interfaces.scheduler;

import com.loopers.application.ranking.RankingAggregator;
import com.loopers.domain.ranking.WeightConfig;
import com.loopers.domain.ranking.WeightConfigRepository;
import com.loopers.infrastructure.ranking.RankingZSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 5분 단위 실시간 랭킹.
 * 현재 시각을 5분 단위로 truncate한 bucket 기준,
 * 최근 1시간(12 bucket) 집계 → ZSET 빌드.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeRankingRefresher {

    private static final long BUCKET_SECONDS = 300;
    private static final long WINDOW_SECONDS = 3600;
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RankingAggregator aggregator;
    private final RankingZSetRepository zSetRepository;
    private final WeightConfigRepository weightConfigRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 60 * 1000)
    public void refresh() {
        Instant now = clock.instant();
        long epochSecond = now.getEpochSecond();
        long currentBucket = epochSecond - (epochSecond % BUCKET_SECONDS);

        LocalDateTime to = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(currentBucket + BUCKET_SECONDS), ZoneOffset.UTC);
        LocalDateTime from = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(currentBucket - WINDOW_SECONDS + BUCKET_SECONDS), ZoneOffset.UTC);

        List<WeightConfig> configs = weightConfigRepository.findAllByActiveTrue();
        if (configs.isEmpty()) {
            configs = List.of(WeightConfig.defaultConfig());
        }

        for (WeightConfig config : configs) {
            Map<Long, Double> scores = aggregator.aggregate(from, to, config);
            String key = "ranking:realtime:" + config.getGroupName();
            zSetRepository.rebuildZSet(key, scores, TTL);
        }

        log.info("Realtime ranking 갱신 완료: from={}, to={}, groups={}", from, to, configs.size());
    }
}
