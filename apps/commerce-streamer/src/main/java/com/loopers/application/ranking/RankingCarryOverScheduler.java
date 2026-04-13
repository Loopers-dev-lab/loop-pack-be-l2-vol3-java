package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final String RANKING_KEY_PREFIX = "ranking:all:";
    private static final String CARRY_OVER_FLAG_PREFIX = "ranking:carryover:done:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double CARRY_OVER_WEIGHT = 0.1;
    private static final double TODAY_PRESERVE_WEIGHT = 1.0;
    private static final long TTL_SECONDS = 172_800; // 2일
    private static final long FLAG_TTL_SECONDS = 172_800; // 2일

    private final ProductRankingRepository productRankingRepository;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void carryOver() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate yesterday = today.minusDays(1);

        String yesterdayKey = RANKING_KEY_PREFIX + yesterday.format(DATE_FORMAT);
        String todayKey = RANKING_KEY_PREFIX + today.format(DATE_FORMAT);
        String flagKey = CARRY_OVER_FLAG_PREFIX + today.format(DATE_FORMAT);

        if (!productRankingRepository.exists(yesterdayKey)) {
            log.info("어제 랭킹 데이터 없음 - carry-over 생략: {}", yesterdayKey);
            return;
        }

        // 플래그 키를 SETNX로 마킹. 이미 존재하면 다른 인스턴스/이전 실행이 처리한 것이므로 생략.
        if (!productRankingRepository.markIfAbsent(flagKey, FLAG_TTL_SECONDS)) {
            log.info("Carry-over 이미 실행됨 - 생략 (멱등성): {}", flagKey);
            return;
        }

        // todayKey 자기 자신을 weight 1.0으로 포함시켜 기존 점수를 보존하면서
        // yesterdayKey의 점수를 weight 0.1로 합산한다.
        productRankingRepository.unionStoreWithWeights(
            todayKey, TODAY_PRESERVE_WEIGHT,
            yesterdayKey, CARRY_OVER_WEIGHT
        );
        productRankingRepository.setTtlIfAbsent(todayKey, TTL_SECONDS);

        log.info("Score carry-over 완료: {} → {} (weight: {})", yesterdayKey, todayKey, CARRY_OVER_WEIGHT);
    }
}
