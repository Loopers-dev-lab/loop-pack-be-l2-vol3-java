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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyCarryOverScheduler {

    private static final double DECAY = 0.1;
    private static final Duration TTL = Duration.ofDays(2);
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingAggregator aggregator;
    private final RankingZSetRepository zSetRepository;
    private final WeightConfigRepository weightConfigRepository;
    private final Clock clock;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);

        LocalDateTime from = BucketTimeUtils.kstDateToUtcBoundary(today);
        LocalDateTime to = BucketTimeUtils.kstDateToUtcBoundary(tomorrow);

        List<WeightConfig> configs = weightConfigRepository.findAllByActiveTrue();
        if (configs.isEmpty()) {
            configs = List.of(WeightConfig.defaultConfig());
        }

        for (WeightConfig config : configs) {
            Map<Long, Double> todayScores = aggregator.aggregate(from, to, config);

            if (todayScores.isEmpty()) {
                continue;
            }

            Map<Long, Double> carryOverScores = todayScores.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() * DECAY));

            String tomorrowKey = "ranking:daily:" + tomorrow.format(KEY_FORMAT) + ":" + config.getGroupName();
            zSetRepository.rebuildZSet(tomorrowKey, carryOverScores, TTL);
        }

        log.info("CarryOver 완료: today={}, tomorrow={}, groups={}", today, tomorrow, configs.size());
    }
}
