package com.loopers.interfaces.scheduler;

import com.loopers.application.metrics.BucketTimeUtils;
import com.loopers.application.ranking.RankingAggregator;
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
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyRankingRefresher {

    private static final Duration TTL = Duration.ofDays(32);
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final RankingAggregator aggregator;
    private final RankingZSetRepository zSetRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void refresh() {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        LocalDateTime from = BucketTimeUtils.kstDateToUtcBoundary(firstOfMonth);
        LocalDateTime to = BucketTimeUtils.kstDateToUtcBoundary(today.plusDays(1));

        Map<Long, Double> scores = aggregator.aggregate(from, to);

        String key = "ranking:monthly:" + today.format(KEY_FORMAT);
        zSetRepository.rebuildZSet(key, scores, TTL);

        log.info("Monthly ranking 갱신 완료: month={}, size={}", today.format(KEY_FORMAT), scores.size());
    }
}
