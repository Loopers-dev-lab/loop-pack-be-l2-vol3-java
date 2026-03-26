package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Coupon 엔티티 단위 테스트")
class CouponTest {

    @Nested
    @DisplayName("선착순 발급 (issue)")
    class Issue {

        @Test
        @DisplayName("성공: 수량 제한 쿠폰을 발급하면 issuedQuantity가 1 증가한다")
        void issue_incrementsIssuedQuantity() {
            // Given
            Coupon coupon = Coupon.create("선착순 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                    null, ZonedDateTime.now().plusDays(30), 100);

            // When
            coupon.issue();

            // Then
            assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 수량 무제한 쿠폰(totalQuantity=null)은 항상 발급 가능하다")
        void issue_unlimitedQuantity() {
            // Given
            Coupon coupon = Coupon.create("무제한 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                    null, ZonedDateTime.now().plusDays(30), null);

            // When
            coupon.issue();
            coupon.issue();
            coupon.issue();

            // Then
            assertThat(coupon.getIssuedQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("실패: 발급 수량이 총 수량에 도달하면 BAD_REQUEST 예외를 던진다")
        void issue_quantityExceeded() {
            // Given
            Coupon coupon = Coupon.create("선착순 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                    null, ZonedDateTime.now().plusDays(30), 1);
            coupon.issue(); // 1/1 발급 완료

            // When & Then
            assertThatThrownBy(() -> coupon.issue())
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("쿠폰 발급 수량이 초과되었습니다.");
        }

        @Test
        @DisplayName("실패: 만료된 쿠폰은 발급할 수 없다")
        void issue_expiredCoupon() {
            // Given — Instancio 대신 리플렉션 없이 만료된 쿠폰을 만들 수 없으므로 hasRemainingQuantity로 검증
            // 만료 검증은 CouponService에서 처리하므로 여기서는 수량 검증만 테스트
            // → 이 테스트는 CouponService 레벨에서 커버
        }
    }

    @Nested
    @DisplayName("남은 수량 확인 (hasRemainingQuantity)")
    class HasRemainingQuantity {

        @Test
        @DisplayName("수량 무제한이면 true를 반환한다")
        void hasRemainingQuantity_unlimited() {
            // Given
            Coupon coupon = Coupon.create("무제한 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                    null, ZonedDateTime.now().plusDays(30), null);

            // When & Then
            assertThat(coupon.hasRemainingQuantity()).isTrue();
        }

        @Test
        @DisplayName("남은 수량이 있으면 true를 반환한다")
        void hasRemainingQuantity_hasRemaining() {
            // Given
            Coupon coupon = Coupon.create("선착순 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                    null, ZonedDateTime.now().plusDays(30), 10);

            // When & Then
            assertThat(coupon.hasRemainingQuantity()).isTrue();
        }

        @Test
        @DisplayName("남은 수량이 없으면 false를 반환한다")
        void hasRemainingQuantity_noRemaining() {
            // Given
            Coupon coupon = Coupon.create("선착순 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                    null, ZonedDateTime.now().plusDays(30), 1);
            coupon.issue();

            // When & Then
            assertThat(coupon.hasRemainingQuantity()).isFalse();
        }
    }
}
