package com.loopers.job.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.domain.ranking.ProductRankingWeeklyRepository;
import com.loopers.infrastructure.ranking.persistence.ProductRankingWeeklyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class ProductRankingWeeklyRepositoryIntegrationTest {

    @Autowired
    private ProductRankingWeeklyRepository productRankingWeeklyRepository;

    @Autowired
    private ProductRankingWeeklyJpaRepository productRankingWeeklyJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주간 랭킹을 저장할 때,")
    @Nested
    class SaveAll {

        @DisplayName("같은 (product_id, score_date)로 두 번 저장하면, score가 덮어쓰기된다.")
        @Test
        void updatesScore_whenDuplicateKey() {
            // arrange
            LocalDate scoreDate = LocalDate.of(2026, 4, 13);
            var firstRanking = ProductRankingWeekly.create(1L, scoreDate, 100.0);
            productRankingWeeklyRepository.saveAll(List.of(firstRanking));

            // act
            var updatedRanking = ProductRankingWeekly.create(1L, scoreDate, 200.0);
            productRankingWeeklyRepository.saveAll(List.of(updatedRanking));

            // assert
            List<ProductRankingWeekly> results = productRankingWeeklyJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getScore()).isEqualTo(200.0)
            );
        }

        @DisplayName("다른 score_date이면, 별도 row로 저장된다.")
        @Test
        void createsSeparateRows_whenDifferentScoreDate() {
            // arrange
            LocalDate date1 = LocalDate.of(2026, 4, 13);
            LocalDate date2 = LocalDate.of(2026, 4, 14);

            // act
            productRankingWeeklyRepository.saveAll(
                    List.of(ProductRankingWeekly.create(1L, date1, 100.0)));
            productRankingWeeklyRepository.saveAll(
                    List.of(ProductRankingWeekly.create(1L, date2, 150.0)));

            // assert
            List<ProductRankingWeekly> results = productRankingWeeklyJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(2),
                    () -> assertThat(results).extracting(ProductRankingWeekly::getScore)
                            .containsExactlyInAnyOrder(100.0, 150.0)
            );
        }
    }
}
