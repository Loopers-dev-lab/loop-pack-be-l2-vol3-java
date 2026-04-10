package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductRankingScoreModelTest {

    @DisplayName("랭킹 점수를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void create_whenValidValues() {
            // act
            ProductRankingScoreModel score = ProductRankingScoreModel.create(
                    1L, LocalDate.of(2026, 4, 5), 0.1);

            // assert
            assertThat(score.getProductId()).isEqualTo(1L);
            assertThat(score.getRankingDate()).isEqualTo(LocalDate.of(2026, 4, 5));
            assertThat(score.getScore()).isEqualTo(0.1);
        }
    }

    @DisplayName("점수를 누적할 때, ")
    @Nested
    class AddScore {

        @DisplayName("delta만큼 점수가 증가한다.")
        @Test
        void addScore_whenPositiveDelta() {
            // arrange
            ProductRankingScoreModel score = ProductRankingScoreModel.create(
                    1L, LocalDate.of(2026, 4, 5), 0.1);

            // act
            score.addScore(0.2);

            // assert
            assertThat(score.getScore()).isCloseTo(0.3, within(0.0001));
        }
    }

    @DisplayName("이월 점수를 생성할 때, ")
    @Nested
    class CreateCarriedOver {

        @DisplayName("전날 점수의 지정 비율로 새 날짜 엔티티가 생성된다.")
        @Test
        void createCarriedOver_whenCalled() {
            // arrange
            ProductRankingScoreModel yesterday = ProductRankingScoreModel.create(
                    1L, LocalDate.of(2026, 4, 5), 1000.0);

            // act
            ProductRankingScoreModel carried = yesterday.createCarriedOver(
                    LocalDate.of(2026, 4, 6), 0.1);

            // assert
            assertThat(carried.getProductId()).isEqualTo(1L);
            assertThat(carried.getRankingDate()).isEqualTo(LocalDate.of(2026, 4, 6));
            assertThat(carried.getScore()).isCloseTo(100.0, within(0.0001));
        }
    }
}
