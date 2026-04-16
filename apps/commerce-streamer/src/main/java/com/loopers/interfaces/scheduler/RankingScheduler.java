package com.loopers.interfaces.scheduler;

import com.loopers.infrastructure.ranking.RankingCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingScheduler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingCacheService rankingCacheService;

    /**
     * 매일 23:50에 오늘 랭킹 점수의 10%를 내일 키로 미리 복사 (콜드 스타트 완화)
     * 자정 이전에 내일 키를 생성해두어 날짜 전환 시점의 빈 랭킹을 방지한다.
     */
    @Scheduled(cron = "0 50 23 * * *")
    public void carryOverRanking() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String tomorrow = LocalDate.now().plusDays(1).format(DATE_FORMATTER);

        log.info("[RankingScheduler] Score Carry-Over 시작: {} → {}", today, tomorrow);
        rankingCacheService.carryOver(today, tomorrow);
        log.info("[RankingScheduler] Score Carry-Over 완료");
    }
}
