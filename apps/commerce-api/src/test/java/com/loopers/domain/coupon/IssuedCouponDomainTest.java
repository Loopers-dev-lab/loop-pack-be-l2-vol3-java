package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@DisplayName("IssuedCoupon 도메인 테스트")
class IssuedCouponDomainTest {

    @Test
    @DisplayName("IssuedCoupon 도메인 클래스가 존재해야 한다")
    void issuedCouponClassShouldExist() {
        try {
            Class.forName("com.loopers.domain.coupon.IssuedCoupon");
        } catch (ClassNotFoundException e) {
            fail("RED: IssuedCoupon 도메인 클래스(com.loopers.domain.coupon.IssuedCoupon)가 아직 없습니다.");
        }
    }

    @Test
    @DisplayName("markUsed 호출 시 상태가 USED로 변경되어야 한다")
    void markUsedTransition() {
        Object issuedCoupon = createIssuedCoupon("AVAILABLE", LocalDateTime.now().plusDays(1));
        try {
            Method markUsed = issuedCoupon.getClass().getDeclaredMethod("markUsed");
            Object updated = markUsed.invoke(issuedCoupon);

            Method status = updated.getClass().getDeclaredMethod("status");
            Object statusValue = status.invoke(updated);
            assertThat(String.valueOf(statusValue)).isEqualTo("USED");
        } catch (NoSuchMethodException e) {
            fail("RED: IssuedCoupon.markUsed()/status() 구현 필요");
        } catch (Exception e) {
            fail("RED: IssuedCoupon 상태 전이 구현 필요. 원인=" + e.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("만료된 IssuedCoupon은 validateUsable에서 거부되어야 한다")
    void expiredIssuedCouponShouldBeRejected() {
        Object issuedCoupon = createIssuedCoupon("AVAILABLE", LocalDateTime.now().minusSeconds(1));
        try {
            Method validateUsable = issuedCoupon.getClass().getDeclaredMethod("validateUsable", LocalDateTime.class);
            validateUsable.invoke(issuedCoupon, LocalDateTime.now());
            fail("RED: 만료 쿠폰 사용 거부 로직이 필요합니다.");
        } catch (NoSuchMethodException e) {
            fail("RED: IssuedCoupon.validateUsable(LocalDateTime) 구현 필요");
        } catch (Exception ignored) {
            assertThat(true).isTrue();
        }
    }

    private static Object createIssuedCoupon(String statusName, LocalDateTime expiredAt) {
        try {
            Class<?> issuedCouponClass = Class.forName("com.loopers.domain.coupon.IssuedCoupon");
            Class<?> statusClass = Class.forName("com.loopers.domain.coupon.CouponStatus");
            Object status = Enum.valueOf((Class<Enum>) statusClass.asSubclass(Enum.class), statusName);

            Constructor<?> ctor = issuedCouponClass.getDeclaredConstructor(
                    String.class,
                    UUID.class,
                    statusClass,
                    LocalDateTime.class,
                    LocalDateTime.class,
                    LocalDateTime.class
            );

            return ctor.newInstance(
                    "member-1",
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    status,
                    LocalDateTime.now(),
                    expiredAt,
                    null
            );
        } catch (Exception e) {
            fail("RED: IssuedCoupon 생성 규약 구현 필요. 원인=" + e.getClass().getSimpleName());
            return null;
        }
    }
}
