package com.loopers.interfaces.scheduler;

import com.loopers.application.ranking.RankingAggregator;
import com.loopers.infrastructure.ranking.RankingZSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyRankingRefresher {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration TTL = Duration.ofDays(8);

    private final RankingAggregator aggregator;
    private final RankingZSetRepository zSetRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void refresh() {
        LocalDate today = LocalDate.now(clock.withZone(KST));

        LocalDateTime from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        Map<Long, Double> scores = aggregator.aggregate(from, to);

        WeekFields iso = WeekFields.ISO;
        int year = today.get(iso.weekBasedYear());
        int week = today.get(iso.weekOfWeekBasedYear());
        String key = String.format("ranking:weekly:%d%02d", year, week);
        zSetRepository.rebuildZSet(key, scores, TTL);

        log.info("Weekly ranking 갱신 완료: year={}, week={}, size={}", year, week, scores.size());
    }
}
