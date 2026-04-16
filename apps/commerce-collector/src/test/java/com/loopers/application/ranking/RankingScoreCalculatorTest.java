package com.loopers.application.ranking;

import com.loopers.event.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RankingScoreCalculator 테스트")
class RankingScoreCalculatorTest {

    private RankingScoreCalculator calculator;
    private RankingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RankingProperties(
            0.1,   // viewWeight
            0.2,   // likeWeight
            0.7,   // orderWeight
            "ranking:all:",  // keyPrefix
            2      // ttlDays
        );
        calculator = new RankingScoreCalculator(properties);
    }

    @Nested
    @DisplayName("조회 이벤트 점수 계산")
    class ViewEvent {

        @Test
        @DisplayName("조회 이벤트는 weight * 1 점수를 반환한다")
        void 조회_점수_계산() {
            // Arrange
            EventType eventType = EventType.PRODUCT_VIEWED;
            Double eventValue = 1.0;

            // Act
            double score = calculator.calculate(eventType, eventValue);

            // Assert
            assertThat(score).isCloseTo(0.1, within(0.0001));
        }
    }

    @Nested
    @DisplayName("좋아요 이벤트 점수 계산")
    class LikeEvent {

        @Test
        @DisplayName("좋아요 생성 이벤트는 weight * 1 점수를 반환한다")
        void 좋아요_생성_점수_계산() {
            // Arrange
            EventType eventType = EventType.LIKE_CREATED;
            Double eventValue = 1.0;

            // Act
            double score = calculator.calculate(eventType, eventValue);

            // Assert
            assertThat(score).isCloseTo(0.2, within(0.0001));
        }

        @Test
        @DisplayName("좋아요 취소 이벤트는 음수 점수를 반환한다")
        void 좋아요_취소_점수_계산() {
            // Arrange
            EventType eventType = EventType.LIKE_CANCELED;
            Double eventValue = 1.0;

            // Act
            double score = calculator.calculate(eventType, eventValue);

            // Assert
            assertThat(score).isCloseTo(-0.2, within(0.0001));
        }
    }

    @Nested
    @DisplayName("주문 이벤트 점수 계산")
    class OrderEvent {

        @Test
        @DisplayName("주문 이벤트는 weight * 1 점수를 반환한다 (건수 누적)")
        void 주문_점수_계산() {
            // Arrange
            EventType eventType = EventType.ORDER_COMPLETED;
            Double eventValue = 10000.0 * 2; // price * quantity (사용되지 않음)

            // Act
            double score = calculator.calculate(eventType, eventValue);

            // Assert
            // 0.7 * 1 = 0.7 (단건 기준)
            assertThat(score).isCloseTo(0.7, within(0.0001));
        }
    }

    @Nested
    @DisplayName("지원하지 않는 이벤트")
    class UnsupportedEvent {

        @Test
        @DisplayName("랭킹 계산 대상이 아닌 이벤트는 0점을 반환한다")
        void 미지원_이벤트_0점() {
            // Arrange
            EventType eventType = EventType.COUPON_ISSUE_REQUESTED;
            Double eventValue = 1.0;

            // Act
            double score = calculator.calculate(eventType, eventValue);

            // Assert
            assertThat(score).isEqualTo(0.0);
        }
    }
}
