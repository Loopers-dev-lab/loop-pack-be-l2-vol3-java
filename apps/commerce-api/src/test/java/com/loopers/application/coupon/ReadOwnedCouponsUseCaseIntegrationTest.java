package com.loopers.application.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.user.UserService;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

class ReadOwnedCouponsUseCaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ReadOwnedCouponsUseCase readOwnedCouponsUseCase;

    @Autowired
    private CouponService couponService;

    @Autowired
    private OwnedCouponService ownedCouponService;

    @Autowired
    private UserService userService;

    @DisplayName("쿠폰 발급 내역을 조회할 때,")
    @Nested
    class Execute {

        @DisplayName("발급 내역이 있으면, 유저 정보가 포함된 결과를 반환한다.")
        @Test
        void returnsResultsWithUserInfo_whenIssuancesExist() {
            // arrange
            var coupon = couponService.create(new com.loopers.domain.coupon.CouponTerms("테스트 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000));
            var user = userService.register(new com.loopers.domain.user.NewUser("testuser1", "Password1!", "홍길동", "1990-01-15", "test@example.com"));
            ownedCouponService.issue(coupon, user.getId());

            // act
            Page<ReadOwnedCouponsUseCase.Result> result = readOwnedCouponsUseCase.execute(coupon.getId(), PageSize.withMaxSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(1),
                    () -> assertThat(result.content().get(0).userId()).isEqualTo(user.getId()),
                    () -> assertThat(result.content().get(0).loginId()).isEqualTo("testuser1"),
                    () -> assertThat(result.content().get(0).userName()).isEqualTo("홍길동"),
                    () -> assertThat(result.content().get(0).status()).isEqualTo("AVAILABLE"),
                    () -> assertThat(result.content().get(0).createdAt()).isNotNull(),
                    () -> assertThat(result.content().get(0).name()).isEqualTo("테스트 쿠폰"),
                    () -> assertThat(result.content().get(0).couponType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(result.content().get(0).discountValue()).isEqualTo(5000L),
                    () -> assertThat(result.content().get(0).minOrderPrice()).isEqualTo(10000L)
            );
        }

        @DisplayName("발급 내역이 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoIssuancesExist() {
            // arrange
            var coupon = couponService.create(new com.loopers.domain.coupon.CouponTerms("빈 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000));

            // act
            Page<ReadOwnedCouponsUseCase.Result> result = readOwnedCouponsUseCase.execute(coupon.getId(), PageSize.withMaxSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }
    }
}
