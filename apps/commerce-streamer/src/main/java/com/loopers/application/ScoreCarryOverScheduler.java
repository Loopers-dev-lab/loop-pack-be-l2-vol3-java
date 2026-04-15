package com.loopers.application;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCarryOverScheduler {

    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RankingRepository rankingRepository;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOverScores() {
        String todayKey = LocalDate.now().format(RankingService.DATE_KEY_FORMATTER);
        String tomorrowKey = LocalDate.now().plusDays(1).format(RankingService.DATE_KEY_FORMATTER);

        try {
            rankingRepository.carryOverScores(todayKey, tomorrowKey, CARRY_OVER_WEIGHT);
            log.info("랭킹 점수 이월 완료: {} -> {} (weight={})", todayKey, tomorrowKey, CARRY_OVER_WEIGHT);
        } catch (Exception e) {
            log.error("랭킹 점수 이월 실패: {} -> {}", todayKey, tomorrowKey, e);
        }
    }
}
