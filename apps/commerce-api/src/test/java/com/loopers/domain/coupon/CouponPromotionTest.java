package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CouponPromotionTest {

    @DisplayName("프로모션 생성 시,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면 정상적으로 생성된다.")
        @Test
        void createsPromotion_whenFieldsAreValid() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();

            // act
            CouponPromotion promotion = CouponPromotion.create(
                    1L, 100, now.minusHours(1), now.plusDays(1)
            );

            // assert
            assertAll(
                    () -> assertThat(promotion.getCouponId()).isEqualTo(1L),
                    () -> assertThat(promotion.getMaxQuantity()).isEqualTo(100),
                    () -> assertThat(promotion.getStartedAt()).isEqualTo(now.minusHours(1)),
                    () -> assertThat(promotion.getEndedAt()).isEqualTo(now.plusDays(1))
            );
        }
    }

    @DisplayName("발급 가능 여부 검증 시,")
    @Nested
    class ValidateIssuable {

        @DisplayName("진행 중인 프로모션이면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPromotionIsActive() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponPromotion promotion = CouponPromotion.create(
                    1L, 100, now.minusHours(1), now.plusDays(1)
            );

            // act & assert
            promotion.validateIssuable();
        }

        @DisplayName("아직 시작되지 않은 프로모션이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPromotionNotStarted() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponPromotion promotion = CouponPromotion.create(
                    1L, 100, now.plusHours(1), now.plusDays(1)
            );

            // act
            CoreException result = assertThrows(CoreException.class, promotion::validateIssuable);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("종료된 프로모션이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPromotionEnded() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponPromotion promotion = CouponPromotion.create(
                    1L, 100, now.minusDays(2), now.minusDays(1)
            );

            // act
            CoreException result = assertThrows(CoreException.class, promotion::validateIssuable);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
