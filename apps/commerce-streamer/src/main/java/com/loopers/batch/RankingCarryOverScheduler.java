package com.loopers.batch;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingCarryOverScheduler {

    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RankingRepository rankingRepository;

    /**
     * 매일 23:50 실행. 오늘 ZSET 점수의 10%를 내일 키에 복사하여 콜드 스타트를 완화한다.
     */
    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        boolean success = rankingRepository.carryOver(today, tomorrow, CARRY_OVER_WEIGHT);

        if (success) {
            log.info("[RankingCarryOver] 성공: {} → {} (weight={})", today, tomorrow, CARRY_OVER_WEIGHT);
        } else {
            log.info("[RankingCarryOver] 스킵: 오늘 키 없음 ({})", today);
        }
    }
}
