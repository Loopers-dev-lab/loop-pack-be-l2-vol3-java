package com.loopers.infrastructure.ranking;

import com.loopers.infrastructure.ranking.RankingRedisRepository.RankingEntry;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 Redis Repository 통합 테스트 — Testcontainers Redis
 *
 * 발제 체크리스트 검증:
 * ✅ ZSET에 점수가 적절하게 반영된다
 * ✅ 일자가 변경되어도 이전 날짜의 랭킹 조회가 정상 동작한다
 * ✅ 가중치 적용이 의도대로 랭킹 순서에 반영된다 (주문 1건 > 좋아요 3건)
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingRedisRepositoryIntegrationTest {

    @Autowired
    private RankingRedisRepository rankingRedisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("ZSET 점수 반영 검증")
    @Nested
    class ZSET_점수_반영 {

        @Test
        void ZREVRANGE로_점수_내림차순_Top_N을_조회한다() {
            // arrange — 3개 상품에 점수 적재
            String key = "ranking:all:20260409";
            redisTemplate.opsForZSet().incrementScore(key, "101", 5.0);
            redisTemplate.opsForZSet().incrementScore(key, "102", 3.0);
            redisTemplate.opsForZSet().incrementScore(key, "103", 8.0);

            // act
            List<RankingEntry> result = rankingRedisRepository.getTopWithScores(key, 0, 2);

            // assert — 점수 내림차순: 103(8.0) > 101(5.0) > 102(3.0)
            assertThat(result).hasSize(3);
            assertThat(result.get(0).productId()).isEqualTo(103L);
            assertThat(result.get(0).score()).isEqualTo(8.0);
            assertThat(result.get(1).productId()).isEqualTo(101L);
            assertThat(result.get(2).productId()).isEqualTo(102L);
        }

        @Test
        void ZINCRBY로_점수가_누적된다() {
            // arrange
            String key = "ranking:all:20260409";
            redisTemplate.opsForZSet().incrementScore(key, "101", 0.1);
            redisTemplate.opsForZSet().incrementScore(key, "101", 0.1);
            redisTemplate.opsForZSet().incrementScore(key, "101", 0.2);

            // act
            Double score = rankingRedisRepository.getScore(key, 101L);

            // assert — 0.1 + 0.1 + 0.2 = 0.4
            assertThat(score).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        void ZREVRANK로_특정_상품의_순위를_조회한다() {
            // arrange
            String key = "ranking:all:20260409";
            redisTemplate.opsForZSet().incrementScore(key, "101", 5.0);
            redisTemplate.opsForZSet().incrementScore(key, "102", 3.0);
            redisTemplate.opsForZSet().incrementScore(key, "103", 8.0);

            // act — 103이 1위(0-based=0), 101이 2위(0-based=1)
            Long rank103 = rankingRedisRepository.getRank(key, 103L);
            Long rank101 = rankingRedisRepository.getRank(key, 101L);
            Long rank102 = rankingRedisRepository.getRank(key, 102L);

            // assert
            assertThat(rank103).isEqualTo(0L); // 1위
            assertThat(rank101).isEqualTo(1L); // 2위
            assertThat(rank102).isEqualTo(2L); // 3위
        }

        @Test
        void ZSET에_없는_상품은_null을_반환한다() {
            String key = "ranking:all:20260409";

            Long rank = rankingRedisRepository.getRank(key, 999L);
            Double score = rankingRedisRepository.getScore(key, 999L);

            assertThat(rank).isNull();
            assertThat(score).isNull();
        }
    }

    @DisplayName("일자 변경 시 이전 날짜 조회 검증")
    @Nested
    class 일자별_키_분리 {

        @Test
        void 서로_다른_날짜의_ZSET은_독립적으로_동작한다() {
            // arrange — 어제와 오늘 키에 각각 데이터 적재
            String yesterdayKey = "ranking:all:20260408";
            String todayKey = "ranking:all:20260409";

            redisTemplate.opsForZSet().incrementScore(yesterdayKey, "101", 10.0);
            redisTemplate.opsForZSet().incrementScore(todayKey, "101", 2.0);
            redisTemplate.opsForZSet().incrementScore(todayKey, "102", 5.0);

            // act
            List<RankingEntry> yesterdayResult = rankingRedisRepository.getTopWithScores(yesterdayKey, 0, 9);
            List<RankingEntry> todayResult = rankingRedisRepository.getTopWithScores(todayKey, 0, 9);

            // assert — 키별 독립 집계
            assertThat(yesterdayResult).hasSize(1);
            assertThat(yesterdayResult.get(0).score()).isEqualTo(10.0);

            assertThat(todayResult).hasSize(2);
            assertThat(todayResult.get(0).productId()).isEqualTo(102L); // 5.0 > 2.0
        }

        @Test
        void 시간_키도_일간_키와_독립적이다() {
            // arrange
            String dailyKey = "ranking:all:20260409";
            String hourlyKey = "ranking:hourly:2026040914";

            redisTemplate.opsForZSet().incrementScore(dailyKey, "101", 10.0);
            redisTemplate.opsForZSet().incrementScore(hourlyKey, "101", 1.0);

            // act
            Double dailyScore = rankingRedisRepository.getScore(dailyKey, 101L);
            Double hourlyScore = rankingRedisRepository.getScore(hourlyKey, 101L);

            // assert — 서로 다른 키이므로 점수가 다르다
            assertThat(dailyScore).isEqualTo(10.0);
            assertThat(hourlyScore).isEqualTo(1.0);
        }
    }

    @DisplayName("가중치 적용이 랭킹 순서에 반영되는지 검증")
    @Nested
    class 가중치_검증 {

        private static final double W_VIEW = 0.1;
        private static final double W_LIKE = 0.2;
        private static final double W_ORDER = 0.6;

        @Test
        void 주문_1건이_좋아요_3건보다_높은_순위를_가진다() {
            // arrange
            String key = "ranking:all:20260409";

            // 상품201: 좋아요 3건 = 0.2 × 3 = 0.6
            redisTemplate.opsForZSet().incrementScore(key, "201", W_LIKE);
            redisTemplate.opsForZSet().incrementScore(key, "201", W_LIKE);
            redisTemplate.opsForZSet().incrementScore(key, "201", W_LIKE);

            // 상품202: 주문 1건 + 조회 1건 = 0.6 + 0.1 = 0.7
            redisTemplate.opsForZSet().incrementScore(key, "202", W_ORDER);
            redisTemplate.opsForZSet().incrementScore(key, "202", W_VIEW);

            // act
            List<RankingEntry> result = rankingRedisRepository.getTopWithScores(key, 0, 1);

            // assert — 202(0.7) > 201(0.6) → 202가 1위
            assertThat(result.get(0).productId()).isEqualTo(202L);
            assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
        }

        @Test
        void 조회_이벤트만으로는_주문_1건을_넘을_수_없다() {
            // arrange
            String key = "ranking:all:20260409";

            // 상품201: 조회 5건 = 0.1 × 5 = 0.5
            for (int i = 0; i < 5; i++) {
                redisTemplate.opsForZSet().incrementScore(key, "201", W_VIEW);
            }

            // 상품202: 주문 1건 = 0.6
            redisTemplate.opsForZSet().incrementScore(key, "202", W_ORDER);

            // act
            List<RankingEntry> result = rankingRedisRepository.getTopWithScores(key, 0, 1);

            // assert — 202(0.6) > 201(0.5)
            assertThat(result.get(0).productId()).isEqualTo(202L);
            assertThat(result.get(0).score()).isEqualTo(0.6);
        }
    }

    @DisplayName("Score Carry-Over 검증")
    @Nested
    class Carry_Over {

        @Test
        void 전날_점수의_10퍼센트를_내일_키에_복사한다() {
            // arrange
            String todayKey = "ranking:all:20260408";
            String tomorrowKey = "ranking:all:20260409";

            redisTemplate.opsForZSet().incrementScore(todayKey, "101", 100.0);
            redisTemplate.opsForZSet().incrementScore(todayKey, "102", 50.0);

            // act — carry-over weight=0.1
            long count = rankingRedisRepository.carryOver(tomorrowKey, todayKey, 0.1, 172800);

            // assert
            assertThat(count).isEqualTo(2);

            Double score101 = rankingRedisRepository.getScore(tomorrowKey, 101L);
            Double score102 = rankingRedisRepository.getScore(tomorrowKey, 102L);

            assertThat(score101).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.001));
            assertThat(score102).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        void 소스_키가_없으면_0을_반환하고_대상_키를_생성하지_않는다() {
            String tomorrowKey = "ranking:all:20260409";

            long count = rankingRedisRepository.carryOver(tomorrowKey, "ranking:all:nonexistent", 0.1, 172800);

            assertThat(count).isEqualTo(0);
            assertThat(rankingRedisRepository.getSize(tomorrowKey)).isEqualTo(0);
        }
    }

    @DisplayName("페이지네이션 검증")
    @Nested
    class 페이지네이션 {

        @Test
        void offset_기반으로_페이지_범위를_조회한다() {
            // arrange — 5개 상품 적재
            String key = "ranking:all:20260409";
            for (int i = 1; i <= 5; i++) {
                redisTemplate.opsForZSet().incrementScore(key, String.valueOf(i), i * 10.0);
            }

            // act — 2페이지 (size=2, start=2, end=3)
            List<RankingEntry> page2 = rankingRedisRepository.getTopWithScores(key, 2, 3);

            // assert — 내림차순: 50,40,30,20,10 → index 2,3 = 30,20
            assertThat(page2).hasSize(2);
            assertThat(page2.get(0).score()).isEqualTo(30.0);
            assertThat(page2.get(1).score()).isEqualTo(20.0);
        }

        @Test
        void ZSET_총_멤버_수를_반환한다() {
            String key = "ranking:all:20260409";
            redisTemplate.opsForZSet().incrementScore(key, "1", 1.0);
            redisTemplate.opsForZSet().incrementScore(key, "2", 2.0);
            redisTemplate.opsForZSet().incrementScore(key, "3", 3.0);

            assertThat(rankingRedisRepository.getSize(key)).isEqualTo(3);
        }
    }
}
