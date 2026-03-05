package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IssuedCouponTest {

    @DisplayName("상태 조회 시,")
    @Nested
    class GetStatus {

        @DisplayName("usedAt이 null이 아니면 USED를 반환한다.")
        @Test
        void returnsUsed_whenUsedAtIsSet() {
            // arrange
            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 1L, LocalDateTime.now().plusDays(30));
            issuedCoupon.markAsUsed();

            // act
            IssuedCoupon.Status status = issuedCoupon.getStatus();

            // assert
            assertThat(status).isEqualTo(IssuedCoupon.Status.USED);
        }

        @DisplayName("만료일이 현재보다 이전이면 EXPIRED를 반환한다.")
        @Test
        void returnsExpired_whenExpiresAtIsInPast() {
            // arrange
            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 1L, LocalDateTime.now().minusSeconds(1));

            // act
            IssuedCoupon.Status status = issuedCoupon.getStatus();

            // assert
            assertThat(status).isEqualTo(IssuedCoupon.Status.EXPIRED);
        }

        @DisplayName("사용되지 않았고 만료되지 않았으면 AVAILABLE을 반환한다.")
        @Test
        void returnsAvailable_whenUnusedAndNotExpired() {
            // arrange
            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 1L, LocalDateTime.now().plusDays(30));

            // act
            IssuedCoupon.Status status = issuedCoupon.getStatus();

            // assert
            assertThat(status).isEqualTo(IssuedCoupon.Status.AVAILABLE);
        }
    }

    @DisplayName("쿠폰 검증 시,")
    @Nested
    class Validate {

        @DisplayName("이미 사용된 쿠폰이면 COUPON_ALREADY_USED 예외가 발생한다.")
        @Test
        void throwsCouponAlreadyUsed_whenAlreadyUsed() {
            // arrange
            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 1L, LocalDateTime.now().plusDays(30));
            issuedCoupon.markAsUsed();

            // act
            CoreException result = assertThrows(CoreException.class, () -> issuedCoupon.validate(1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ALREADY_USED);
        }

        @DisplayName("만료된 쿠폰이면 COUPON_EXPIRED 예외가 발생한다.")
        @Test
        void throwsCouponExpired_whenExpired() {
            // arrange
            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 1L, LocalDateTime.now().minusSeconds(1));

            // act
            CoreException result = assertThrows(CoreException.class, () -> issuedCoupon.validate(1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_EXPIRED);
        }

        @DisplayName("소유자가 다르면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            // arrange
            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 1L, LocalDateTime.now().plusDays(30));

            // act
            CoreException result = assertThrows(CoreException.class, () -> issuedCoupon.validate(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("유효한 쿠폰이고 소유자이면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenValidAndOwner() {
            // arrange
            IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 1L, LocalDateTime.now().plusDays(30));

            // act & assert
            assertDoesNotThrow(() -> issuedCoupon.validate(1L));
        }
    }
}
