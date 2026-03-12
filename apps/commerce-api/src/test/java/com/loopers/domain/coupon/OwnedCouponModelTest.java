package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OwnedCouponModel 단위 테스트")
class OwnedCouponModelTest {

    private CouponModel createCoupon() {
        return CouponModel.create(
                "테스트 쿠폰", CouponDiscountType.FIXED, 5000L,
                null, 1000, ZonedDateTime.now().plusDays(30));
    }

    @DisplayName("생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 AVAILABLE 상태로 생성되고 스냅샷이 복사된다")
        @Test
        void create_whenValidValues() {
            // arrange
            CouponModel coupon = createCoupon();

            // act
            OwnedCouponModel owned = OwnedCouponModel.create(coupon, 100L);

            // assert
            assertAll(
                    () -> assertThat(owned.getCouponName()).isEqualTo("테스트 쿠폰"),
                    () -> assertThat(owned.getDiscountType()).isEqualTo(CouponDiscountType.FIXED),
                    () -> assertThat(owned.getDiscountValue()).isEqualTo(5000L),
                    () -> assertThat(owned.getUserId()).isEqualTo(100L),
                    () -> assertThat(owned.isAvailable()).isTrue(),
                    () -> assertThat(owned.getOrderId()).isNull(),
                    () -> assertThat(owned.getUsedAt()).isNull());
        }
    }

    @DisplayName("사용할 때, ")
    @Nested
    class Use {

        @DisplayName("미사용 상태이고 본인 소유이면 orderId/usedAt이 기록된다")
        @Test
        void use_success() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);

            // act
            owned.use(100L, 1L);

            // assert
            assertAll(
                    () -> assertThat(owned.isUsed()).isTrue(),
                    () -> assertThat(owned.getUsedAt()).isNotNull(),
                    () -> assertThat(owned.getOrderId()).isEqualTo(1L));
        }

        @DisplayName("이미 사용된 쿠폰을 재사용하면 예외가 발생한다")
        @Test
        void use_whenAlreadyUsed() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);
            owned.use(100L, 1L);

            // act & assert
            assertThatThrownBy(() -> owned.use(100L, 2L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이미 사용된 쿠폰입니다.");
        }

        @DisplayName("본인 소유가 아닌 쿠폰을 사용하면 예외가 발생한다")
        @Test
        void use_whenNotOwned() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);

            // act & assert
            assertThatThrownBy(() -> owned.use(999L, 1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인 소유의 쿠폰이 아닙니다.");
        }

        @DisplayName("만료된 쿠폰을 사용하면 예외가 발생한다")
        @Test
        void use_whenExpired() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);
            setField(owned, "expiredAt", ZonedDateTime.now().minusDays(1));

            // act & assert
            assertThatThrownBy(() -> owned.use(100L, 1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("만료된 쿠폰입니다.");
        }
    }

    @DisplayName("복원할 때, ")
    @Nested
    class Restore {

        @DisplayName("사용된 쿠폰이면 orderId/usedAt이 초기화된다")
        @Test
        void restore_whenUsed() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);
            owned.use(100L, 1L);

            // act
            owned.restore();

            // assert
            assertAll(
                    () -> assertThat(owned.getOrderId()).isNull(),
                    () -> assertThat(owned.getUsedAt()).isNull(),
                    () -> assertThat(owned.isAvailable()).isTrue());
        }

        @DisplayName("만료된 쿠폰을 복원하면 파생 상태가 EXPIRED이다")
        @Test
        void restore_whenExpired() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);
            owned.use(100L, 1L);
            setField(owned, "expiredAt", ZonedDateTime.now().minusDays(1));

            // act
            owned.restore();

            // assert
            assertAll(
                    () -> assertThat(owned.getOrderId()).isNull(),
                    () -> assertThat(owned.isExpired()).isTrue(),
                    () -> assertThat(owned.getStatus()).isEqualTo("EXPIRED"));
        }

        @DisplayName("미사용 쿠폰을 복원하면 예외가 발생한다")
        @Test
        void restore_whenNotUsed() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);

            // act & assert
            assertThatThrownBy(() -> owned.restore())
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("복원할 수 없는 쿠폰입니다.");
        }
    }

    @DisplayName("할인 금액을 계산할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액(FIXED) 할인은 discountValue를 반환한다")
        @Test
        void calculateDiscount_fixed() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);

            // act & assert
            assertThat(owned.calculateDiscount(50000L)).isEqualTo(5000L);
        }

        @DisplayName("정률(RATE) 할인은 주문 금액의 비율을 반환한다")
        @Test
        void calculateDiscount_rate() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "10% 할인", CouponDiscountType.RATE, 10L,
                    null, 1000, ZonedDateTime.now().plusDays(30));
            OwnedCouponModel owned = OwnedCouponModel.create(coupon, 100L);

            // act & assert
            assertThat(owned.calculateDiscount(50000L)).isEqualTo(5000L);
        }

        @DisplayName("할인 금액은 주문 금액을 초과하지 않는다")
        @Test
        void calculateDiscount_notExceedOrderAmount() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "10000원 할인", CouponDiscountType.FIXED, 10000L,
                    null, 1000, ZonedDateTime.now().plusDays(30));
            OwnedCouponModel owned = OwnedCouponModel.create(coupon, 100L);

            // act & assert
            assertThat(owned.calculateDiscount(3000L)).isEqualTo(3000L);
        }
    }

    @DisplayName("최소 주문 금액을 검증할 때, ")
    @Nested
    class ValidateMinOrderAmount {

        @DisplayName("최소 주문 금액 미달 시 예외가 발생한다")
        @Test
        void validateMinOrderAmount_notMet() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 5000L,
                    20000L, 1000, ZonedDateTime.now().plusDays(30));
            OwnedCouponModel owned = OwnedCouponModel.create(coupon, 100L);

            // act & assert
            assertThatThrownBy(() -> owned.validateMinOrderAmount(10000L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("최소 주문 금액을 충족하지 않습니다.");
        }

        @DisplayName("최소 주문 금액이 없으면 통과한다")
        @Test
        void validateMinOrderAmount_noMinimum() {
            // arrange
            OwnedCouponModel owned = OwnedCouponModel.create(createCoupon(), 100L);

            // act & assert (예외 없이 통과)
            owned.validateMinOrderAmount(1000L);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
