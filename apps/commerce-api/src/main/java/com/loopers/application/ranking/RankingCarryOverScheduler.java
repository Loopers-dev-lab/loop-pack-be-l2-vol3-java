package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final double CARRY_OVER_RATIO = 0.1;

    private final RankingRepository rankingRepository;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        log.info("랭킹 carry-over 시작. from={}, to={}, ratio={}", today, tomorrow, CARRY_OVER_RATIO);
        rankingRepository.carryOver(today, tomorrow, CARRY_OVER_RATIO);
        log.info("랭킹 carry-over 완료. from={}, to={}", today, tomorrow);
    }
}
