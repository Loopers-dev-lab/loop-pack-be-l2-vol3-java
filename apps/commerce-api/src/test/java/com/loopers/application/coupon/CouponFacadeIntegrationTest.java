package com.loopers.application.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("CouponFacade 통합 테스트")
class CouponFacadeIntegrationTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long couponId;

    @BeforeEach
    void setUp() {
        Coupon coupon = couponRepository.save(Coupon.create(
                "테스트 쿠폰",
                DiscountType.FIXED,
                Money.of(1000L),
                Money.of(5000L),
                null,
                5,
                ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusDays(30)
        ));
        couponId = coupon.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("쿠폰 발급")
    class IssueCouponTest {

        @Test
        @DisplayName("쿠폰을 발급할 수 있다")
        void issueCoupon_success() {
            IssuedCoupon issued = couponFacade.issueCoupon(couponId, 1L);

            assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
            assertThat(issued.getCouponId()).isEqualTo(couponId);
            assertThat(issued.getUserId()).isEqualTo(1L);

            Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("중복 발급 시 예외가 발생한다")
        void issueCoupon_duplicate_throwsException() {
            couponFacade.issueCoupon(couponId, 1L);

            assertThatThrownBy(() -> couponFacade.issueCoupon(couponId, 1L))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("발급 수량 초과 시 예외가 발생한다")
        void issueCoupon_exceedQuantity_throwsException() {
            for (long userId = 1; userId <= 5; userId++) {
                couponFacade.issueCoupon(couponId, userId);
            }

            assertThatThrownBy(() -> couponFacade.issueCoupon(couponId, 6L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
