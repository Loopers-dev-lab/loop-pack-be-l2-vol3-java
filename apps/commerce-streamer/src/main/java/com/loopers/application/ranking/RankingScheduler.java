package com.loopers.application.ranking;

import com.loopers.domain.metrics.service.MetricsService;
import com.loopers.domain.ranking.model.ProductMetrics;
import com.loopers.domain.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingScheduler {

    private final MetricsService metricsService;
    private final RankingService rankingService;

    private LocalDateTime lastExecutedAt = LocalDateTime.now().minusMinutes(2);

    @Scheduled(fixedDelay = 60_000)
    public void syncRankings() {
        LocalDateTime now = LocalDateTime.now();
        List<ProductMetrics> changed = metricsService.findChangedAfter(lastExecutedAt);
        if (changed.isEmpty()) {
            lastExecutedAt = now;
            return;
        }

        for (ProductMetrics metrics : changed) {
            rankingService.updateScore(metrics);
        }
        rankingService.refreshTtl();

        lastExecutedAt = now;
        log.info("랭킹 동기화 완료 - {}건 갱신", changed.size());
    }

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOverScores() {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
        String today = LocalDate.now().format(dateFormat);
        String tomorrow = LocalDate.now().plusDays(1).format(dateFormat);
        rankingService.carryOverScores(today, tomorrow);
        log.info("콜드 스타트 Carry-Over 완료 - {} → {}", today, tomorrow);
    }
}
