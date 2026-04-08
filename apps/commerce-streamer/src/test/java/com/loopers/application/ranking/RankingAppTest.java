package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductDailySignalRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingWeightProperties;
import com.loopers.domain.ranking.ScoreAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RankingApp 단위 테스트")
class RankingAppTest {

    private RankingRepository rankingRepository;
    private ProductDailySignalRepository productDailySignalRepository;
    private RankingApp rankingApp;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        productDailySignalRepository = mock(ProductDailySignalRepository.class);
        RankingWeightProperties weights = new RankingWeightProperties(0.1, 0.2, 0.7);
        ScoreAggregator scoreAggregator = new ScoreAggregator(weights);
        rankingApp = new RankingApp(rankingRepository, scoreAggregator, productDailySignalRepository);
    }

    @Nested
    @DisplayName("applyLikeDelta()")
    class ApplyLikeDelta {

        @Test
        @DisplayName("LikedEvent(delta=1)는 +0.2 점수(likeWeight * 1)로 증가시키고 DB에 적재한다")
        void likedEventIncreasesScoreByWeight() {
            Long productDbId = 42L;
            LocalDate date = LocalDate.of(2026, 4, 5);

            rankingApp.applyLikeDelta(productDbId, 1, date);

            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRepository).incrementScore(
                    org.mockito.ArgumentMatchers.eq(date),
                    org.mockito.ArgumentMatchers.eq(productDbId),
                    scoreCaptor.capture()
            );
            assertThat(scoreCaptor.getValue()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.001));
            verify(productDailySignalRepository).upsertLikeCount(productDbId, date, 1);
        }

        @Test
        @DisplayName("LikeRemovedEvent(delta=-1)는 차감하지 않고 DB에도 적재하지 않는다")
        void likeRemovedEventDoesNotDeduceScore() {
            Long productDbId = 42L;
            LocalDate date = LocalDate.of(2026, 4, 5);

            rankingApp.applyLikeDelta(productDbId, -1, date);

            verify(rankingRepository, never()).incrementScore(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyDouble()
            );
            verify(productDailySignalRepository, never()).upsertLikeCount(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyLong()
            );
        }
    }

    @Nested
    @DisplayName("applyViewScore()")
    class ApplyViewScore {

        @Test
        @DisplayName("View 점수는 viewWeight(0.1)로 고정 증가하고 DB에 적재한다")
        void viewScoreIsViewWeight() {
            Long productDbId = 42L;
            LocalDate date = LocalDate.of(2026, 4, 5);

            rankingApp.applyViewScore(productDbId, date);

            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRepository).incrementScore(
                    org.mockito.ArgumentMatchers.eq(date),
                    org.mockito.ArgumentMatchers.eq(productDbId),
                    scoreCaptor.capture()
            );
            assertThat(scoreCaptor.getValue()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.001));
            verify(productDailySignalRepository).upsertViewCount(productDbId, date, 1);
        }
    }

    @Nested
    @DisplayName("applyOrderScore()")
    class ApplyOrderScore {

        @Test
        @DisplayName("주문 점수는 0.7 * price * quantity로 계산되고 DB에 적재한다")
        void orderScoreIsOrderWeightTimesPriceTimesQuantity() {
            Long productDbId = 42L;
            BigDecimal price = new BigDecimal("10000");
            int quantity = 2;
            LocalDate date = LocalDate.of(2026, 4, 5);

            rankingApp.applyOrderScore(productDbId, price, quantity, date);

            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRepository).incrementScore(
                    org.mockito.ArgumentMatchers.eq(date),
                    org.mockito.ArgumentMatchers.eq(productDbId),
                    scoreCaptor.capture()
            );
            assertThat(scoreCaptor.getValue()).isCloseTo(14000.0, org.assertj.core.data.Offset.offset(0.001));
            verify(productDailySignalRepository).upsertOrderAmount(productDbId, date, 20000.0);
        }
    }
}
