package com.loopers.application.ranking;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RankingAppService 조회 통합 테스트")
class RankingAppServiceTest {

    @Autowired
    private RankingAppService rankingAppService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private final String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    private final String todayKey = "ranking:all:" + today;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("Top-N 랭킹 조회")
    class GetTopRankings {

        @BeforeEach
        void setUp() {
            redisTemplate.opsForZSet().add(todayKey, "101", 50.0);
            redisTemplate.opsForZSet().add(todayKey, "202", 30.0);
            redisTemplate.opsForZSet().add(todayKey, "303", 80.0);
            redisTemplate.opsForZSet().add(todayKey, "404", 10.0);
            redisTemplate.opsForZSet().add(todayKey, "505", 60.0);
        }

        @Test
        @DisplayName("score 내림차순으로 Top-N 상품 ID를 반환한다")
        void returnsTopNByScoreDesc() {
            List<RankingEntry> result = rankingAppService.getTopRankings(today, 0, 3);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).productId()).isEqualTo(303L);
            assertThat(result.get(1).productId()).isEqualTo(505L);
            assertThat(result.get(2).productId()).isEqualTo(101L);
        }

        @Test
        @DisplayName("페이지네이션이 정상 동작한다 (2페이지)")
        void pagination() {
            List<RankingEntry> result = rankingAppService.getTopRankings(today, 1, 3);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).productId()).isEqualTo(202L);
            assertThat(result.get(1).productId()).isEqualTo(404L);
        }

        @Test
        @DisplayName("각 항목에 순위와 점수가 포함된다")
        void containsRankAndScore() {
            List<RankingEntry> result = rankingAppService.getTopRankings(today, 0, 3);

            assertThat(result.get(0).rank()).isEqualTo(1);
            assertThat(result.get(0).score()).isEqualTo(80.0);
            assertThat(result.get(2).rank()).isEqualTo(3);
            assertThat(result.get(2).score()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("특정 상품 순위 조회")
    class GetProductRank {

        @BeforeEach
        void setUp() {
            redisTemplate.opsForZSet().add(todayKey, "101", 50.0);
            redisTemplate.opsForZSet().add(todayKey, "202", 30.0);
            redisTemplate.opsForZSet().add(todayKey, "303", 80.0);
        }

        @Test
        @DisplayName("해당 상품의 순위를 반환한다 (1-based)")
        void returnsRank() {
            Long rank = rankingAppService.getProductRank(today, 303L);

            assertThat(rank).isEqualTo(1L);
        }

        @Test
        @DisplayName("랭킹에 없는 상품은 null을 반환한다")
        void returnsNullForUnranked() {
            Long rank = rankingAppService.getProductRank(today, 999L);

            assertThat(rank).isNull();
        }
    }

    @Nested
    @DisplayName("이전 날짜 랭킹 조회")
    class PreviousDateRanking {

        @Test
        @DisplayName("이전 날짜의 랭킹도 정상 조회된다")
        void queryPreviousDate() {
            String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
            String yesterdayKey = "ranking:all:" + yesterday;
            redisTemplate.opsForZSet().add(yesterdayKey, "101", 100.0);

            List<RankingEntry> result = rankingAppService.getTopRankings(yesterday, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).productId()).isEqualTo(101L);
        }
    }
}
