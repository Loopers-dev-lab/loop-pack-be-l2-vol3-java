package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingWeightProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("RankingApp 단위 테스트")
class RankingAppTest {

    private RankingRepository rankingRepository;
    private RankingApp rankingApp;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        RankingWeightProperties weights = new RankingWeightProperties(0.1, 0.2, 0.7);
        rankingApp = new RankingApp(rankingRepository, weights);
    }

    @Nested
    @DisplayName("applyLikeDelta()")
    class ApplyLikeDelta {

        @Test
        @DisplayName("LikedEvent(delta=1)는 +0.2 점수(likeWeight * 1)로 증가시킨다")
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
            assertThat(scoreCaptor.getValue()).isEqualTo(0.2);
        }

        @Test
        @DisplayName("LikeRemovedEvent(delta=-1)는 -0.2 점수로 차감시킨다")
        void likeRemovedEventDecreasesScoreByWeight() {
            Long productDbId = 42L;
            LocalDate date = LocalDate.of(2026, 4, 5);

            rankingApp.applyLikeDelta(productDbId, -1, date);

            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRepository).incrementScore(
                    org.mockito.ArgumentMatchers.eq(date),
                    org.mockito.ArgumentMatchers.eq(productDbId),
                    scoreCaptor.capture()
            );
            assertThat(scoreCaptor.getValue()).isEqualTo(-0.2);
        }
    }

    @Nested
    @DisplayName("applyViewScore()")
    class ApplyViewScore {

        @Test
        @DisplayName("View 점수는 viewWeight(0.1)로 고정 증가한다")
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
            assertThat(scoreCaptor.getValue()).isEqualTo(0.1);
        }
    }

    @Nested
    @DisplayName("applyOrderScore()")
    class ApplyOrderScore {

        @Test
        @DisplayName("주문 점수는 0.7 * price * quantity로 계산된다")
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
            // 0.7 * 10000 * 2 = 14000.0
            assertThat(scoreCaptor.getValue()).isEqualTo(14000.0);
        }
    }
}
