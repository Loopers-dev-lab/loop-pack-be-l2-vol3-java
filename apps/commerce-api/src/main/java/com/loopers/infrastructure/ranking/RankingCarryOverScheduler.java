package com.loopers.infrastructure.ranking;

import com.loopers.config.RankingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 랭킹 Score Carry-Over 스케줄러
 *
 * 매일 23:50(KST)에 오늘의 랭킹 점수 일부를 내일 키에 복사한다.
 * 콜드 스타트 문제 완화: 날짜가 바뀌어도 빈 랭킹이 아닌 어제의 인기 상품이 초기 노출된다.
 *
 * carry-over weight = 0.1 (오늘 점수의 10%)
 * - 오늘 100점 상품 → 내일 시작 시 10점
 * - 내일 새로운 이벤트가 쌓이면 carry-over 점수는 상대적으로 희석됨
 * - 가중치가 작아 오늘의 점수가 상위로 올라가지 못하는 문제 방지
 */
@Component
public class RankingCarryOverScheduler {

    private static final Logger log = LoggerFactory.getLogger(RankingCarryOverScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingProperties rankingProperties;

    public RankingCarryOverScheduler(RankingRedisRepository rankingRedisRepository,
                                      RankingProperties rankingProperties) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.rankingProperties = rankingProperties;
    }

    /**
     * 23:50 KST — 오늘 점수의 10%를 내일 키에 복사
     *
     * 23:50인 이유: 자정 전에 미리 실행하여 00:00 시점에 빈 랭킹 방지.
     * 23:50~00:00 사이의 이벤트는 오늘 키에 반영되고, carry-over에는 포함되지 않지만
     * 10분간의 누락은 랭킹 근사치 특성상 무시할 수 있는 수준.
     */
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOver() {
        LocalDate today = LocalDate.now(KST);
        LocalDate tomorrow = today.plusDays(1);

        String todayKey = rankingProperties.getKeyPrefix() + ":" + today.format(DATE_FORMAT);
        String tomorrowKey = rankingProperties.getKeyPrefix() + ":" + tomorrow.format(DATE_FORMAT);
        long ttlSeconds = TimeUnit.DAYS.toSeconds(rankingProperties.getTtlDays());

        long count = rankingRedisRepository.carryOver(tomorrowKey, todayKey, CARRY_OVER_WEIGHT, ttlSeconds);

        log.info("[Ranking] Carry-over 완료 — {} → {} ({}건, weight={})",
                todayKey, tomorrowKey, count, CARRY_OVER_WEIGHT);
    }
}
