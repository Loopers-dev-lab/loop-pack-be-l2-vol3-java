package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.WeeklyRankingRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(MySqlTestContainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JpaWeeklyRankingRepositoryTest {

    @Autowired
    private WeeklyRankingRepository weeklyRankingRepository;

    @Autowired
    private WeeklyRankingJpaRepository weeklyRankingJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("findProductIdsByBaseDate() 를 호출할 때,")
    @Nested
    class FindProductIdsByBaseDate {

        @DisplayName("rank 오름차순으로 productId 목록을 반환한다.")
        @Test
        void returnsInRankOrder() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 4, 13);
            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(100L, 1, 300.0, 10, 5, 100, 5000, baseDate));
            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(200L, 2, 200.0, 8, 3, 80, 3000, baseDate));
            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(300L, 3, 100.0, 5, 1, 50, 1000, baseDate));

            // act
            List<Long> result = weeklyRankingRepository.findProductIdsByBaseDate(baseDate, 0, 3);

            // assert
            assertThat(result).containsExactly(100L, 200L, 300L);
        }

        @DisplayName("offset 과 limit 에 따라 해당 페이지만 반환한다.")
        @Test
        void withOffsetAndLimit() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 4, 13);
            for (int rank = 1; rank <= 5; rank++) {
                weeklyRankingJpaRepository.save(MvProductRankWeekly.of((long) rank * 10, rank, 100.0 - rank, 1, 1, 1, 1000, baseDate));
            }

            // act
            List<Long> result = weeklyRankingRepository.findProductIdsByBaseDate(baseDate, 2, 2);

            // assert
            assertThat(result).containsExactly(30L, 40L);
        }

        @DisplayName("해당 baseDate 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoData() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 4, 13);

            // act
            List<Long> result = weeklyRankingRepository.findProductIdsByBaseDate(baseDate, 0, 10);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("countByBaseDate() 를 호출할 때,")
    @Nested
    class CountByBaseDate {

        @DisplayName("해당 baseDate 의 전체 row 수를 반환한다.")
        @Test
        void returnsTotalCount() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 4, 13);
            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(1L, 1, 100.0, 1, 1, 1, 1000, baseDate));
            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(2L, 2, 90.0, 1, 1, 1, 900, baseDate));

            // act
            long count = weeklyRankingRepository.countByBaseDate(baseDate);

            // assert
            assertThat(count).isEqualTo(2);
        }

        @DisplayName("해당 baseDate 데이터가 없으면 0을 반환한다.")
        @Test
        void returnsZero_whenNoData() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 4, 13);

            // act
            long count = weeklyRankingRepository.countByBaseDate(baseDate);

            // assert
            assertThat(count).isZero();
        }
    }
}
