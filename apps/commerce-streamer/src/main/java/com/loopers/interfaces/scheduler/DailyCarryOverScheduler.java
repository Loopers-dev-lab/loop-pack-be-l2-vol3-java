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
import java.util.stream.Collectors;

/**
 * 자정 직후 빈 ZSET 문제 완화.
 * 전날 상위 상품의 점수를 10%만 가져와서 다음 날 ZSET 초기값으로 적재.
 * DailyRankingRefresher가 5분 뒤에 실시간 데이터로 덮어쓰므로
 * carry-over 데이터는 새벽 5분간만 서빙됨.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyCarryOverScheduler {

    private static final double DECAY = 0.1;
    private static final Duration TTL = Duration.ofDays(2);
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingAggregator aggregator;
    private final RankingZSetRepository zSetRepository;
    private final Clock clock;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);

        LocalDateTime from = BucketTimeUtils.kstDateToUtcBoundary(today);
        LocalDateTime to = BucketTimeUtils.kstDateToUtcBoundary(tomorrow);

        Map<Long, Double> todayScores = aggregator.aggregate(from, to);

        if (todayScores.isEmpty()) {
            log.warn("CarryOver: 오늘 데이터 없음, 스킵");
            return;
        }

        Map<Long, Double> carryOverScores = todayScores.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() * DECAY
                ));

        String tomorrowKey = "ranking:daily:" + tomorrow.format(KEY_FORMAT);
        zSetRepository.rebuildZSet(tomorrowKey, carryOverScores, TTL);

        log.info("CarryOver 완료: today={}, tomorrow={}, products={}", today, tomorrow, carryOverScores.size());
    }
}
