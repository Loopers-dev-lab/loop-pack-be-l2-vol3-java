package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingReader;
import com.loopers.domain.ranking.RankingWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.LongAdder;

/**
 * 콜드 스타트 완화 — 매일 23:50 KST 에 "오늘 키" 의 점수를 1% 가중으로 "내일 키" 에 미리 시드한다.
 *
 * 자정 직후 새 일간 키가 비어 있는 구간을 제거하여, 상품 상세의 `dailyRank` 가
 * 전혀 없는 상태로 잠시 노출되는 문제를 완화한다.
 *
 * <p>idempotency:
 * 단일 인스턴스 환경에서는 같은 날 두 번 실행되어도 결과가 동일하다 (같은 값으로 덮어쓰기).
 * 분산 환경에서 두 인스턴스가 동시에 실행되는 경우, 순회 사이에 새 ZADD 가 끼면 시드 값이
 * 미세하게 달라질 수 있으나 1% 가중 시드의 노이즈 수준이라 운영상 허용한다.
 * (정확한 분산 락이 필요하면 `scheduler_lock` 를 활용해 단일 실행을 보장할 것.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final double CARRY_OVER_FACTOR = 0.01;

    private final RankingReader rankingReader;
    private final RankingWriter rankingWriter;
    private final Clock clock;

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOver() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        carryOverFor(today);
    }

    /**
     * 지정 날짜(today) 의 점수를 next-day 키에 시드한다. 테스트에서 직접 호출 가능.
     */
    public void carryOverFor(LocalDate today) {
        if (today == null) return;
        String todayKey = RankingKey.daily(today);
        String tomorrowKey = RankingKey.daily(today.plusDays(1));

        LongAdder count = new LongAdder();
        try {
            rankingReader.forEachWithScore(todayKey, (productId, score) -> {
                rankingWriter.upsertScore(tomorrowKey, productId, score * CARRY_OVER_FACTOR);
                count.increment();
            });
            log.info("ranking carry-over 완료: from={} to={} seeded={}", todayKey, tomorrowKey, count.sum());
        } catch (Exception e) {
            log.warn("ranking carry-over 실패: from={} to={}", todayKey, tomorrowKey, e);
        }
    }
}
