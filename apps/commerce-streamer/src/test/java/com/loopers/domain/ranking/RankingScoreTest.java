package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RankingScoreTest {

    @DisplayName("RankingScore를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("productId가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            assertThatThrownBy(() -> new RankingScore(null, 1.0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("상품 ID는 필수입니다.");
        }

        @DisplayName("정상적으로 생성된다.")
        @Test
        void createsSuccessfully() {
            // act
            RankingScore score = new RankingScore(1L, 0.5);

            // assert
            assertThat(score.productId()).isEqualTo(1L);
            assertThat(score.score()).isEqualTo(0.5);
        }
    }

    @DisplayName("점수를 합산할 때,")
    @Nested
    class Merge {

        @DisplayName("같은 상품이면, 점수를 합산한다.")
        @Test
        void mergesScores_whenSameProductId() {
            // arrange
            RankingScore score1 = new RankingScore(1L, 0.3);
            RankingScore score2 = new RankingScore(1L, 0.5);

            // act
            RankingScore merged = score1.merge(score2);

            // assert
            assertThat(merged.productId()).isEqualTo(1L);
            assertThat(merged.score()).isEqualTo(0.8);
        }

        @DisplayName("다른 상품이면, 예외가 발생한다.")
        @Test
        void throwsException_whenDifferentProductId() {
            // arrange
            RankingScore score1 = new RankingScore(1L, 0.3);
            RankingScore score2 = new RankingScore(2L, 0.5);

            // act & assert
            assertThatThrownBy(() -> score1.merge(score2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("서로 다른 상품의 점수는 합산할 수 없습니다.");
        }
    }

    @DisplayName("점수를 감쇠할 때,")
    @Nested
    class Decay {

        @DisplayName("감쇠 계수를 적용한 새 RankingScore를 반환한다.")
        @Test
        void appliesDecayFactor() {
            // arrange
            RankingScore score = new RankingScore(1L, 100.0);

            // act
            RankingScore decayed = score.decay(0.01);

            // assert
            assertThat(decayed.productId()).isEqualTo(1L);
            assertThat(decayed.score()).isCloseTo(1.0, offset(0.001));
        }
    }

    @DisplayName("점수 목록을 합산할 때,")
    @Nested
    class MergeAll {

        @DisplayName("같은 productId의 점수를 합산한다.")
        @Test
        void mergesScoresByProductId() {
            // arrange
            List<RankingScore> scores = List.of(
                    new RankingScore(1L, 0.1),
                    new RankingScore(1L, 0.2),
                    new RankingScore(2L, 0.3)
            );

            // act
            List<RankingScore> merged = RankingScore.mergeAll(scores);

            // assert
            assertThat(merged).hasSize(2);
            assertThat(findByProductId(merged, 1L).score()).isCloseTo(0.3, offset(0.001));
            assertThat(findByProductId(merged, 2L).score()).isCloseTo(0.3, offset(0.001));
        }

        @DisplayName("합산 결과가 0.0인 항목은 제외한다.")
        @Test
        void excludesZeroScores() {
            // arrange
            List<RankingScore> scores = List.of(
                    new RankingScore(1L, 0.2),
                    new RankingScore(1L, -0.2),
                    new RankingScore(2L, 0.1)
            );

            // act
            List<RankingScore> merged = RankingScore.mergeAll(scores);

            // assert
            assertThat(merged).extracting(RankingScore::productId).containsExactly(2L);
        }

        @DisplayName("빈 리스트이면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmpty_whenEmpty() {
            assertThat(RankingScore.mergeAll(Collections.emptyList())).isEmpty();
        }
    }

    private RankingScore findByProductId(List<RankingScore> scores, Long productId) {
        return scores.stream()
                .filter(s -> s.productId().equals(productId))
                .findFirst()
                .orElseThrow();
    }
}
