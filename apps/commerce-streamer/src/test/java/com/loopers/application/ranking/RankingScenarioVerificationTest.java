package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("랭킹 시나리오 검증 테스트 (남은 TODO 실측)")
class RankingScenarioVerificationTest {

    @Autowired
    private RankingApp rankingApp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("TODO 4-2: 고가 상품 독식 검증")
    class ExpensiveProductDomination {

        @Test
        @DisplayName("저가 대량 판매 vs 고가 소량 판매 — 금액 기반 score 비교")
        void cheapVsExpensive() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            // 시나리오: 저가상품(1000원)이 50개 팔림 vs 고가상품(100,000원)이 3개 팔림
            // 저가: 50건의 주문 이벤트, 각 quantity=1
            for (int i = 0; i < 50; i++) {
                rankingApp.applyOrderScore(1L, new BigDecimal("1000"), 1, date);
            }
            // 고가: 3건의 주문 이벤트, 각 quantity=1
            for (int i = 0; i < 3; i++) {
                rankingApp.applyOrderScore(2L, new BigDecimal("100000"), 1, date);
            }

            Double cheapScore = redisTemplate.opsForZSet().score(key, "1");
            Double expensiveScore = redisTemplate.opsForZSet().score(key, "2");

            // 저가: 0.7 * 1000 * 1 * 50건 = 35,000
            // 고가: 0.7 * 100000 * 1 * 3건 = 210,000
            System.out.println("[DOMINATION TEST] cheap(1000원*50건) score=" + cheapScore);
            System.out.println("[DOMINATION TEST] expensive(100000원*3건) score=" + expensiveScore);
            System.out.println("[DOMINATION TEST] ratio expensive/cheap=" + (expensiveScore / cheapScore));

            assertThat(cheapScore).isEqualTo(35000.0);
            assertThat(expensiveScore).isEqualTo(210000.0);
            // 결론: 6배 차이 - 고가 상품이 판매량 10배 적어도 랭킹을 차지 (독식 확인)
        }

        @Test
        @DisplayName("극단 시나리오: 고가 10만원 1건 vs 저가 1000원 100건")
        void extremeCase() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            // 저가: 100건 팔림
            for (int i = 0; i < 100; i++) {
                rankingApp.applyOrderScore(1L, new BigDecimal("1000"), 1, date);
            }
            // 고가: 1건 팔림
            rankingApp.applyOrderScore(2L, new BigDecimal("100000"), 1, date);

            Double cheapScore = redisTemplate.opsForZSet().score(key, "1");
            Double expensiveScore = redisTemplate.opsForZSet().score(key, "2");

            // 저가: 0.7 * 1000 * 100 = 70,000
            // 고가: 0.7 * 100000 * 1 = 70,000
            // → 정확히 동점 (100건 팔린 저가가 1건 팔린 고가와 동등)
            System.out.println("[EXTREME] cheap(1000원*100건) score=" + cheapScore);
            System.out.println("[EXTREME] expensive(100000원*1건) score=" + expensiveScore);

            assertThat(cheapScore).isEqualTo(70000.0);
            assertThat(expensiveScore).isEqualTo(70000.0);
            // 결론: 가격 차이만큼 판매량 차이가 있어야 동등 → 인기도 왜곡 가능성 확인
        }

        @Test
        @DisplayName("log 정규화 시뮬레이션 — 0.7 * log(price * quantity)")
        void logNormalizationSimulation() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            // 저가(1000원) 100건 vs 고가(100000원) 1건 시나리오에서 log 정규화 비교
            // log(1000 * 1) ≈ 6.91, log(100000 * 1) ≈ 11.51
            // log 정규화 시: 0.7 * 6.91 * 100 = 483, 0.7 * 11.51 * 1 = 8
            // → log 정규화는 판매 건수가 지배적 요인이 됨

            double cheapLog = 0.0;
            for (int i = 0; i < 100; i++) {
                cheapLog += Math.log(1000.0 * 1) * 0.7;
            }
            double expensiveLog = Math.log(100000.0 * 1) * 0.7;

            System.out.println("[LOG-NORMALIZATION SIMULATION]");
            System.out.println("  cheap(1000원*100건) log-score=" + cheapLog);
            System.out.println("  expensive(100000원*1건) log-score=" + expensiveLog);
            System.out.println("  ratio cheap/expensive=" + (cheapLog / expensiveLog));

            assertThat(cheapLog).isGreaterThan(expensiveLog);
            // 결론: log 정규화 시 판매 건수가 더 지배적 → 현재 선형 방식이 매출 중심, log는 판매량 중심
        }
    }

    @Nested
    @DisplayName("TODO 6-1: offset vs cursor 페이징 비교 (실시간 변경 환경)")
    class OffsetVsCursorPagination {

        @Test
        @DisplayName("페이징 중 새 상품 상위권 진입 시 offset 방식은 중복/누락 발생")
        void offsetPaginationSkewedByRealtimeInsertion() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            // 초기 데이터: 10개 상품, 점수 100, 90, 80, ..., 10
            for (int i = 1; i <= 10; i++) {
                redisTemplate.opsForZSet().add(key, String.valueOf(i), (11 - i) * 10.0);
            }

            // 1페이지(offset=0, size=5) 조회
            Set<ZSetOperations.TypedTuple<String>> page1 =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 4);
            List<String> page1Ids = extractIds(page1);

            // *** 실시간 변경 발생: 새 상품(11번)이 상위권 진입 (score=95) ***
            redisTemplate.opsForZSet().add(key, "11", 95.0);

            // 2페이지(offset=5, size=5) 조회
            Set<ZSetOperations.TypedTuple<String>> page2Offset =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 5, 9);
            List<String> page2OffsetIds = extractIds(page2Offset);

            System.out.println("[OFFSET] page1=" + page1Ids);
            System.out.println("[OFFSET] page2 after insert=" + page2OffsetIds);

            // 1페이지+2페이지 합친 결과: 변경으로 중복이 발생하거나 누락이 생김
            List<String> combined = new ArrayList<>(page1Ids);
            combined.addAll(page2OffsetIds);
            Set<String> uniqueIds = new HashSet<>(combined);

            // 1페이지: [1(100),2(90),3(80),4(70),5(60)]
            // 삽입 후 ZSET: [1(100),11(95),2(90),3(80),4(70),5(60),6(50),7(40),8(30),9(20),10(10)]
            // 2페이지(offset 5, size 5): [5(60),6(50),7(40),8(30),9(20)]
            // 합계 10개 이지만 5번이 중복됨
            System.out.println("[OFFSET] combined.size=" + combined.size() + ", unique.size=" + uniqueIds.size());
            System.out.println("[OFFSET] duplicates=" + (combined.size() - uniqueIds.size()));
            assertThat(combined).containsAll(List.of("1", "2", "3", "4", "5"));
            // 중복 발생 확인
            assertThat(combined.size() - uniqueIds.size()).isGreaterThan(0);
        }

        @Test
        @DisplayName("cursor 방식은 실시간 변경에도 중복/누락 없이 일관성 유지")
        void cursorPaginationConsistentWithRealtimeInsertion() {
            LocalDate date = LocalDate.of(2026, 4, 5);

            // 초기 데이터: 10개 상품, 점수 100, 90, ..., 10
            String key = RankingKeyGenerator.dailyKey(date);
            for (int i = 1; i <= 10; i++) {
                redisTemplate.opsForZSet().add(key, String.valueOf(i), (11 - i) * 10.0);
            }

            // Streamer에는 읽기 API가 없으므로 raw Redis 명령으로 cursor 시뮬레이션
            var raw1 = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                    key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 5);
            List<String> page1Ids = extractIds(raw1);
            Double lastScore = raw1.stream()
                    .reduce((a, b) -> b)
                    .map(ZSetOperations.TypedTuple::getScore)
                    .orElse(null);

            // *** 실시간 변경: 새 상품 상위권 진입 ***
            redisTemplate.opsForZSet().add(key, "11", 95.0);

            // 2페이지 cursor 조회 (score < lastScore)
            var raw2 = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                    key, Double.NEGATIVE_INFINITY, lastScore, 1, 5);
            List<String> page2Ids = extractIds(raw2);

            List<String> combined = new ArrayList<>(page1Ids);
            combined.addAll(page2Ids);
            Set<String> uniqueIds = new HashSet<>(combined);

            System.out.println("[CURSOR] page1=" + page1Ids + " (lastScore=" + lastScore + ")");
            System.out.println("[CURSOR] page2 after insert=" + page2Ids);
            System.out.println("[CURSOR] combined.size=" + combined.size() + ", unique.size=" + uniqueIds.size());
            System.out.println("[CURSOR] duplicates=" + (combined.size() - uniqueIds.size()));

            // cursor는 score 기준으로 페이징하므로 새로 삽입된 상품(95)은 1페이지 score 범위보다 높아서 자동 제외
            assertThat(combined.size() - uniqueIds.size()).isZero(); // 중복 없음
        }

        private List<String> extractIds(Set<ZSetOperations.TypedTuple<String>> tuples) {
            List<String> ids = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                ids.add(tuple.getValue());
            }
            return ids;
        }
    }

    @Nested
    @DisplayName("TODO 9-4: 대량 데이터 콜드 스타트 검증")
    class ColdStartLargeScale {

        @Test
        @DisplayName("1000개 상품 ZSET → carry-over → 내일 상위 랭킹 유지 검증")
        void largeScaleCarryOver() {
            LocalDate today = LocalDate.of(2026, 4, 5);
            LocalDate tomorrow = today.plusDays(1);
            String todayKey = RankingKeyGenerator.dailyKey(today);
            String tomorrowKey = RankingKeyGenerator.dailyKey(tomorrow);

            // 1000개 상품 점수 seed (1~1000)
            for (int i = 1; i <= 1000; i++) {
                redisTemplate.opsForZSet().add(todayKey, String.valueOf(i), i * 10.0);
            }

            // Carry-Over 실행 (weight=0.1)
            long start = System.nanoTime();
            long count = rankingApp.carryOver(today, tomorrow, 0.1);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            System.out.println("[COLD-START] carry-over elapsed=" + elapsedMs + "ms, members=" + count);

            // 내일 ZSET도 1000개 상품이 존재
            Long tomorrowCard = redisTemplate.opsForZSet().zCard(tomorrowKey);
            assertThat(tomorrowCard).isEqualTo(1000L);

            // Top-10 순위는 오늘과 동일 (weight는 상대 순위를 바꾸지 않음)
            var todayTop10 = redisTemplate.opsForZSet().reverseRangeWithScores(todayKey, 0, 9);
            var tomorrowTop10 = redisTemplate.opsForZSet().reverseRangeWithScores(tomorrowKey, 0, 9);

            // 순위 비교
            var todayIds = todayTop10.stream().map(ZSetOperations.TypedTuple::getValue).toList();
            var tomorrowIds = tomorrowTop10.stream().map(ZSetOperations.TypedTuple::getValue).toList();
            assertThat(tomorrowIds).isEqualTo(todayIds);

            // 점수는 1/10로 축소됨
            Double todayTop1 = todayTop10.iterator().next().getScore();
            Double tomorrowTop1 = tomorrowTop10.iterator().next().getScore();
            assertThat(tomorrowTop1).isCloseTo(todayTop1 * 0.1, org.assertj.core.data.Offset.offset(0.001));

            System.out.println("[COLD-START] today top1 score=" + todayTop1
                    + ", tomorrow top1 score=" + tomorrowTop1
                    + " (ratio=" + (tomorrowTop1 / todayTop1) + ")");
        }

        @Test
        @DisplayName("자정 직후 빈 ZSET vs carry-over 적용 ZSET 비교 (콜드 스타트 Before/After)")
        void coldStartBeforeAfter() {
            LocalDate today = LocalDate.of(2026, 4, 5);
            LocalDate tomorrow = today.plusDays(1);
            String tomorrowKey = RankingKeyGenerator.dailyKey(tomorrow);

            // Before: carry-over 없이 자정 직후 → 빈 랭킹
            var beforeTuples = redisTemplate.opsForZSet().reverseRangeWithScores(tomorrowKey, 0, 9);
            System.out.println("[BEFORE] tomorrow ranking size=" + (beforeTuples == null ? 0 : beforeTuples.size()));
            assertThat(beforeTuples).isNullOrEmpty();

            // 오늘 데이터 시드
            String todayKey = RankingKeyGenerator.dailyKey(today);
            for (int i = 1; i <= 100; i++) {
                redisTemplate.opsForZSet().add(todayKey, String.valueOf(i), i * 5.0);
            }

            // Carry-Over 적용
            rankingApp.carryOver(today, tomorrow, 0.1);

            // After: carry-over 후 → 전날 상위 상품으로 초기 랭킹 형성
            var afterTuples = redisTemplate.opsForZSet().reverseRangeWithScores(tomorrowKey, 0, 9);
            System.out.println("[AFTER] tomorrow ranking size=" + afterTuples.size());
            assertThat(afterTuples).hasSize(10);
        }
    }
}
