package com.loopers.interfaces.scheduler;

import com.loopers.application.metrics.BucketTimeUtils;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class HourlyRankingRefresher {

    private static final Duration TTL = Duration.ofHours(3);

    private final RankingAggregator aggregator;
    private final RankingZSetRepository zSetRepository;
    private final WeightConfigRepository weightConfigRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void refresh() {
        Instant now = clock.instant();
        long epochSecond = now.getEpochSecond();
        long truncatedHour = epochSecond - (epochSecond % 3600);
        Instant hourBucket = Instant.ofEpochSecond(truncatedHour);

        LocalDateTime from = LocalDateTime.ofInstant(hourBucket, ZoneOffset.UTC);
        LocalDateTime to = LocalDateTime.ofInstant(hourBucket.plusSeconds(3600), ZoneOffset.UTC);

        List<WeightConfig> configs = weightConfigRepository.findAllByActiveTrue();
        if (configs.isEmpty()) {
            configs = List.of(WeightConfig.defaultConfig());
        }

        String hourKey = String.format("%d", truncatedHour);

        for (WeightConfig config : configs) {
            Map<Long, Double> scores = aggregator.aggregate(from, to, config);
            String key = "ranking:hourly:" + hourKey + ":" + config.getGroupName();
            zSetRepository.rebuildZSet(key, scores, TTL);
        }

        log.info("Hourly ranking 갱신 완료: hour={}, groups={}", from, configs.size());
    }
}
