package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RankingWeight 단위 테스트")
class RankingWeightTest {

    @ParameterizedTest
    @CsvSource({
        "PRODUCT_VIEWED, 0.1",
        "PRODUCT_LIKED, 0.2",
        "PRODUCT_UNLIKED, 0.2",
        "ORDER_COMPLETED, 0.6"
    })
    @DisplayName("이벤트 타입에 따라 올바른 가중치를 반환한다")
    void returnsCorrectWeight(String eventType, double expectedWeight) {
        // Act
        RankingWeight weight = RankingWeight.fromEventType(eventType);

        // Assert
        assertThat(weight.getWeight()).isEqualTo(expectedWeight);
    }

    @Test
    @DisplayName("조회 1회의 가중치 점수는 0.1이다")
    void viewScoreIsPointOne() {
        // Act
        double score = RankingWeight.VIEW.calculate(1.0);

        // Assert
        assertThat(score).isEqualTo(0.1);
    }

    @Test
    @DisplayName("좋아요 1회의 가중치 점수는 0.2이다")
    void likeScoreIsPointTwo() {
        // Act
        double score = RankingWeight.LIKE.calculate(1.0);

        // Assert
        assertThat(score).isEqualTo(0.2);
    }

    @Test
    @DisplayName("주문 1건(50,000원)의 가중치 점수는 30,000이다")
    void orderScoreIs30000() {
        // Act
        double score = RankingWeight.ORDER.calculate(50000.0);

        // Assert
        assertThat(score).isEqualTo(30000.0);
    }

    @Test
    @DisplayName("주문 1건 > 좋아요 3건 가중치 비교")
    void orderOneIsGreaterThanThreeLikes() {
        // Arrange
        double orderScore = RankingWeight.ORDER.calculate(50000.0);
        double threeLikesScore = RankingWeight.LIKE.calculate(1.0) * 3;

        // Assert
        assertThat(orderScore).isGreaterThan(threeLikesScore);
    }

    @Test
    @DisplayName("좋아요 취소 시 음수 점수를 반환한다")
    void unlikeReturnsNegativeScore() {
        // Act
        RankingWeight weight = RankingWeight.fromEventType("PRODUCT_UNLIKED");
        double score = weight.calculate(-1.0);

        // Assert
        assertThat(score).isEqualTo(-0.2);
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입이면 예외가 발생한다")
    void throwsOnUnknownEventType() {
        // Act & Assert
        assertThatThrownBy(() -> RankingWeight.fromEventType("UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("알 수 없는 이벤트 타입");
    }
}
