package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private RankingRepository rankingRepository;

    private String todayKey() {
        return "ranking:all:" + LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Nested
    @DisplayName("조회 점수 반영")
    class AddViewScore {

        @Test
        @DisplayName("조회 시 0.1 점수가 오늘 키에 반영된다")
        void addViewScore() {
            // when
            rankingService.addViewScore(1L);

            // then
            verify(rankingRepository)
                    .incrementScore(eq(todayKey()), eq(1L), eq(0.1));
            verify(rankingRepository)
                    .setTtl(eq(todayKey()), eq(2 * 24 * 60 * 60L));
        }
    }

    @Nested
    @DisplayName("좋아요 점수 반영")
    class AddLikeScore {

        @Test
        @DisplayName("좋아요 시 0.2 점수가 오늘 키에 반영된다")
        void addLikeScore() {
            // when
            rankingService.addLikeScore(1L);

            // then
            verify(rankingRepository)
                    .incrementScore(eq(todayKey()), eq(1L), eq(0.2));
        }
    }

    @Nested
    @DisplayName("주문 점수 반영")
    class AddOrderScore {

        @Test
        @DisplayName("주문 시 0.7 * quantity 점수가 반영된다")
        void addOrderScore() {
            // when
            rankingService.addOrderScore(1L, 3);

            // then
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRepository)
                    .incrementScore(eq(todayKey()), eq(1L), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(0.7 * 3);
        }
    }

    @Nested
    @DisplayName("가중치 순서 검증")
    class WeightOrder {

        @Test
        @DisplayName("주문 1건(0.7)은 좋아요 3건(0.6)보다 높다")
        void orderWeightIsHigherThanLikes() {
            double orderScore = RankingScoreWeight.ORDER * 1;   // 0.7
            double likeScore = RankingScoreWeight.LIKE * 3;     // 0.6
            assertThat(orderScore).isGreaterThan(likeScore);
        }

        @Test
        @DisplayName("좋아요 1건(0.2)은 조회 1건(0.1)보다 높다")
        void likeWeightIsHigherThanView() {
            assertThat(RankingScoreWeight.LIKE)
                    .isGreaterThan(RankingScoreWeight.VIEW);
        }
    }
}
