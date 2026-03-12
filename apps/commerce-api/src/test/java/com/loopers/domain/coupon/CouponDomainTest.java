package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@DisplayName("Coupon 도메인 테스트")
class CouponDomainTest {

    @Test
    @DisplayName("Coupon 도메인 클래스가 존재해야 한다")
    void couponClassShouldExist() {
        try {
            Class.forName("com.loopers.domain.coupon.Coupon");
        } catch (ClassNotFoundException e) {
            fail("RED: Coupon 도메인 클래스(com.loopers.domain.coupon.Coupon)가 아직 없습니다.");
        }
    }

    @Nested
    @DisplayName("할인 계산")
    class Discount {
        @Test
        @DisplayName("정액 쿠폰은 고정 금액을 할인한다")
        void fixedDiscount() {
            int discount = invokeCalculateDiscount("FIXED", 3_000, 10_000, LocalDateTime.now().plusDays(1), 20_000);
            assertThat(discount).isEqualTo(3_000);
        }

        @Test
        @DisplayName("정률 쿠폰은 퍼센트로 할인한다")
        void rateDiscount() {
            int discount = invokeCalculateDiscount("RATE", 10, 10_000, LocalDateTime.now().plusDays(1), 30_000);
            assertThat(discount).isEqualTo(3_000);
        }

        @Test
        @DisplayName("최소 주문 금액 미달이면 할인 0")
        void belowMinOrderAmount() {
            int discount = invokeCalculateDiscount("FIXED", 3_000, 10_000, LocalDateTime.now().plusDays(1), 9_000);
            assertThat(discount).isZero();
        }
    }

    @Nested
    @DisplayName("사용 가능성")
    class Usable {
        @Test
        @DisplayName("만료 후 사용 불가")
        void expired() {
            boolean usable = invokeIsUsableAt("FIXED", 1_000, 0, LocalDateTime.now().minusSeconds(1), LocalDateTime.now());
            assertThat(usable).isFalse();
        }

        @Test
        @DisplayName("만료 전 사용 가능")
        void beforeExpired() {
            boolean usable = invokeIsUsableAt("FIXED", 1_000, 0, LocalDateTime.now().plusSeconds(1), LocalDateTime.now());
            assertThat(usable).isTrue();
        }
    }

    private static Object createCoupon(String typeName, int value, int minOrderAmount, LocalDateTime expiredAt) {
        try {
            Class<?> couponClass = Class.forName("com.loopers.domain.coupon.Coupon");
            Class<?> couponTypeClass = Class.forName("com.loopers.domain.coupon.CouponType");
            Object couponType = Enum.valueOf((Class<Enum>) couponTypeClass.asSubclass(Enum.class), typeName);

            Constructor<?> ctor = couponClass.getDeclaredConstructor(
                    String.class,
                    couponTypeClass,
                    int.class,
                    int.class,
                    LocalDateTime.class
            );
            return ctor.newInstance("테스트 쿠폰", couponType, value, minOrderAmount, expiredAt);
        } catch (Exception e) {
            fail("RED: Coupon 생성 규약 구현 필요. 원인=" + e.getClass().getSimpleName());
            return null;
        }
    }

    private static int invokeCalculateDiscount(String typeName, int value, int minOrderAmount, LocalDateTime expiredAt, int orderAmount) {
        try {
            Object coupon = createCoupon(typeName, value, minOrderAmount, expiredAt);
            Method m = coupon.getClass().getDeclaredMethod("calculateDiscount", int.class);
            return (int) m.invoke(coupon, orderAmount);
        } catch (Exception e) {
            fail("RED: Coupon.calculateDiscount(int) 구현 필요. 원인=" + e.getClass().getSimpleName());
            return -1;
        }
    }

    private static boolean invokeIsUsableAt(String typeName, int value, int minOrderAmount, LocalDateTime expiredAt, LocalDateTime now) {
        try {
            Object coupon = createCoupon(typeName, value, minOrderAmount, expiredAt);
            Method m = coupon.getClass().getDeclaredMethod("isUsableAt", LocalDateTime.class);
            return (boolean) m.invoke(coupon, now);
        } catch (Exception e) {
            fail("RED: Coupon.isUsableAt(LocalDateTime) 구현 필요. 원인=" + e.getClass().getSimpleName());
            return false;
        }
    }
}
