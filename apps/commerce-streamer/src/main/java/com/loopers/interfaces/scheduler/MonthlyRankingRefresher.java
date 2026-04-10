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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyRankingRefresher {

    private static final Duration TTL = Duration.ofDays(32);
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final RankingAggregator aggregator;
    private final RankingZSetRepository zSetRepository;
    private final WeightConfigRepository weightConfigRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void refresh() {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        LocalDateTime from = BucketTimeUtils.kstDateToUtcBoundary(firstOfMonth);
        LocalDateTime to = BucketTimeUtils.kstDateToUtcBoundary(today.plusDays(1));

        List<WeightConfig> configs = weightConfigRepository.findAllByActiveTrue();
        if (configs.isEmpty()) {
            configs = List.of(WeightConfig.defaultConfig());
        }

        for (WeightConfig config : configs) {
            Map<Long, Double> scores = aggregator.aggregate(from, to, config);
            String key = "ranking:monthly:" + today.format(KEY_FORMAT) + ":" + config.getGroupName();
            zSetRepository.rebuildZSet(key, scores, TTL);
        }

        log.info("Monthly ranking 갱신 완료: month={}, groups={}", today.format(KEY_FORMAT), configs.size());
    }
}
