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

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.ProductRankingMonthly;
import com.loopers.domain.ranking.ProductRankingMonthlyRepository;
import com.loopers.infrastructure.ranking.persistence.ProductRankingMonthlyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
class ProductRankingMonthlyRepositoryIntegrationTest {

    @Autowired
    private ProductRankingMonthlyRepository productRankingMonthlyRepository;

    @Autowired
    private ProductRankingMonthlyJpaRepository productRankingMonthlyJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("월간 랭킹을 저장할 때,")
    @Nested
    class SaveAll {

        @DisplayName("같은 (product_id, score_date)로 두 번 저장하면, score가 덮어쓰기된다.")
        @Test
        void updatesScore_whenDuplicateKey() {
            // arrange
            LocalDate scoreDate = LocalDate.of(2026, 4, 14);
            var firstRanking = ProductRankingMonthly.create(1L, scoreDate, 100.0);
            productRankingMonthlyRepository.saveAll(List.of(firstRanking));

            // act
            var updatedRanking = ProductRankingMonthly.create(1L, scoreDate, 200.0);
            productRankingMonthlyRepository.saveAll(List.of(updatedRanking));

            // assert
            List<ProductRankingMonthly> results = productRankingMonthlyJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getScore()).isEqualTo(200.0)
            );
        }

        @DisplayName("다른 score_date이면, 별도 row로 저장된다.")
        @Test
        void createsSeparateRows_whenDifferentScoreDate() {
            // arrange
            LocalDate date1 = LocalDate.of(2026, 4, 14);
            LocalDate date2 = LocalDate.of(2026, 4, 15);

            // act
            productRankingMonthlyRepository.saveAll(
                    List.of(ProductRankingMonthly.create(1L, date1, 100.0)));
            productRankingMonthlyRepository.saveAll(
                    List.of(ProductRankingMonthly.create(1L, date2, 150.0)));

            // assert
            List<ProductRankingMonthly> results = productRankingMonthlyJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(2),
                    () -> assertThat(results).extracting(ProductRankingMonthly::getScore)
                            .containsExactlyInAnyOrder(100.0, 150.0)
            );
        }
    }
}
