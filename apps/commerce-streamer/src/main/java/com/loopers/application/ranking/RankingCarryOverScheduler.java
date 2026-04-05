package com.loopers.application.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RankingApp rankingApp;

    /**
     * 매일 23:50에 오늘 ZSET을 내일 ZSET에 carry-over 한다.
     * 전날 상위 상품이 자정 직후 빈 랭킹으로 나타나는 콜드 스타트 완화.
     */
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "rankingCarryOver", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        long count = rankingApp.carryOver(today, tomorrow, CARRY_OVER_WEIGHT);
        log.info("[RANKING_CARRY_OVER] {} → {}, weight={}, members={}", today, tomorrow, CARRY_OVER_WEIGHT, count);
    }
}
