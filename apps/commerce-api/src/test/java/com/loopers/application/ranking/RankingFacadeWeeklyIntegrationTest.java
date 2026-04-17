package com.loopers.application.ranking;

import com.loopers.fixture.WeeklyRankTestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingFacadeWeeklyIntegrationTest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 4, 16);

    @Autowired
    private RankingFacade rankingFacade;

    @Autowired
    private WeeklyRankTestFixture fixture;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        stringRedisTemplate.delete("rankings:weekly:latest_date");
        stringRedisTemplate.delete("rankings:weekly:%s:0:20".formatted(SNAPSHOT));
    }

    @Nested
    @DisplayName("findWeeklyRanking()")
    class FindWeeklyRanking {

        @Test
        @DisplayName("date 미지정 + latest_date 캐시 hit → 해당 snapshot 반환")
        void dateOmitted_cacheHit() {
            // arrange
            stringRedisTemplate.opsForValue().set("rankings:weekly:latest_date", SNAPSHOT.toString());
            fixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            RankingResult response = rankingFacade.findWeeklyRanking(null, 0, 20);

            // assert
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).rank()).isEqualTo(1);
        }

        @Test
        @DisplayName("date 미지정 + latest_date 캐시 miss → DB MAX 쿼리 폴백 + 캐시 put")
        void dateOmitted_cacheMiss_dbFallback() {
            // arrange
            fixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            RankingResult response = rankingFacade.findWeeklyRanking(null, 0, 20);

            // assert
            assertThat(response.items()).hasSize(1);
            String cached = stringRedisTemplate.opsForValue().get("rankings:weekly:latest_date");
            assertThat(cached).isEqualTo(SNAPSHOT.toString());
        }

        @Test
        @DisplayName("date 명시 → 해당 snapshot 반환")
        void dateSpecified() {
            // arrange
            fixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            RankingResult response = rankingFacade.findWeeklyRanking(SNAPSHOT, 0, 20);

            // assert
            assertThat(response.items()).hasSize(1);
        }

        @Test
        @DisplayName("snapshot 없음 → 빈 리스트 + totalElements=0")
        void emptySnapshot() {
            // act
            RankingResult response = rankingFacade.findWeeklyRanking(SNAPSHOT, 0, 20);

            // assert
            assertThat(response.items()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("동일 쿼리 두 번 호출 → 두 번째는 메인 캐시 hit")
        void mainCacheHit() {
            // arrange
            fixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            rankingFacade.findWeeklyRanking(SNAPSHOT, 0, 20);
            String cacheKey = "rankings:weekly:%s:0:20".formatted(SNAPSHOT);

            // assert
            assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();
        }
    }
}
