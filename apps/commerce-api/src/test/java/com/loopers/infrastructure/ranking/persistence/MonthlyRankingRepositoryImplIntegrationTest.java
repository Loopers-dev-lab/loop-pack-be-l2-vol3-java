package com.loopers.infrastructure.ranking.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.ProductRankingMonthly;
import com.loopers.support.BaseIntegrationTest;

@DisplayName("MonthlyRankingRepositoryImpl 통합 테스트")
class MonthlyRankingRepositoryImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MonthlyRankingRepository monthlyRankingRepository;

    @Autowired
    private MonthlyRankingJpaRepository monthlyRankingJpaRepository;

    private static final LocalDate SCORE_DATE = LocalDate.of(2026, 4, 14);

    @DisplayName("상위 랭킹을 조회할 때,")
    @Nested
    class ReadTopRanked {

        @DisplayName("해당 scoreDate의 데이터를 score 내림차순으로 반환한다.")
        @Test
        void returnsItemsByScoreDateInDescendingOrder() {
            // arrange
            LocalDate otherDate = LocalDate.of(2026, 4, 13);
            saveRanking(1L, otherDate, 99.0);
            saveRanking(10L, SCORE_DATE, 70.0);
            saveRanking(20L, SCORE_DATE, 58.4);
            saveRanking(30L, SCORE_DATE, 45.2);

            // act
            List<ProductRankingMonthly> rankings = monthlyRankingRepository.readTopRanked(SCORE_DATE, 0, 10);

            // assert
            assertAll(
                    () -> assertThat(rankings).hasSize(3),
                    () -> assertThat(rankings.get(0).getProductId()).isEqualTo(10L),
                    () -> assertThat(rankings.get(0).getScore()).isEqualTo(70.0),
                    () -> assertThat(rankings.get(1).getProductId()).isEqualTo(20L),
                    () -> assertThat(rankings.get(2).getProductId()).isEqualTo(30L)
            );
        }

        @DisplayName("page를 지정하면, 해당 페이지의 데이터를 반환한다.")
        @Test
        void returnsItemsByPage() {
            // arrange
            saveRanking(1L, SCORE_DATE, 70.0);
            saveRanking(2L, SCORE_DATE, 58.4);
            saveRanking(3L, SCORE_DATE, 45.2);
            saveRanking(4L, SCORE_DATE, 30.0);
            saveRanking(5L, SCORE_DATE, 15.5);

            // act
            List<ProductRankingMonthly> rankings = monthlyRankingRepository.readTopRanked(SCORE_DATE, 1, 2);

            // assert
            assertAll(
                    () -> assertThat(rankings).hasSize(2),
                    () -> assertThat(rankings.get(0).getProductId()).isEqualTo(3L),
                    () -> assertThat(rankings.get(1).getProductId()).isEqualTo(4L)
            );
        }

        @DisplayName("데이터가 없으면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenNoData() {
            // act
            List<ProductRankingMonthly> rankings = monthlyRankingRepository.readTopRanked(SCORE_DATE, 0, 10);

            // assert
            assertThat(rankings).isEmpty();
        }
    }

    private void saveRanking(Long productId, LocalDate scoreDate, Double score) {
        monthlyRankingJpaRepository.save(ProductRankingMonthly.create(productId, scoreDate, score));
    }
}
