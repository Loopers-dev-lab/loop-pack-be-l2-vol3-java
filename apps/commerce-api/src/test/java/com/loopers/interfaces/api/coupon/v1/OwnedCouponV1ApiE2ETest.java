package com.loopers.interfaces.api.coupon.v1;

import static com.loopers.interfaces.api.coupon.v1.CouponSteps.createCoupon;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.getMyOwnedCoupons;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.issueCoupon;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponFixture;
import com.loopers.domain.coupon.OwnedCouponRepository;
import com.loopers.interfaces.api.coupon.v1.CouponDto.CreateCouponRequest;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;

class OwnedCouponV1ApiE2ETest extends BaseE2ETest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OwnedCouponRepository ownedCouponRepository;

    private HttpHeaders userHeaders;

    @BeforeEach
    void setUp() {
        var signUpRequest = new UserV1Dto.SignUpRequest(
                "testuser1", "Password1!", "홍길동", "1990-01-15", "test@example.com"
        );
        signUp(testRestTemplate, signUpRequest);
        userHeaders = userAuthHeaders(signUpRequest.loginId(), signUpRequest.password());
    }

    @DisplayName("GET /api/v1/owned-coupons")
    @Nested
    class GetMyOwnedCoupons {

        @DisplayName("보유한 쿠폰 목록을 조회하면, 쿠폰 상세 정보와 함께 200 응답을 받는다.")
        @Test
        void returns200_withOwnedCoupons() {
            // arrange
            var couponId1 = createCoupon(testRestTemplate, new CreateCouponRequest(
                    "사용 가능 쿠폰",
                    CouponType.FIXED,
                    5000L,
                    null,
                    10000L,
                    ZonedDateTime.now().plusDays(30),
                    10000
            ));
            var couponId2 = createCoupon(testRestTemplate, new CreateCouponRequest(
                    "만료된 쿠폰",
                    CouponType.RATE,
                    10L,
                    5000L,
                    20000L,
                    ZonedDateTime.now().plusDays(30),
                    10000
            ));
            // Phase 4: 쿠폰 발급이 비동기(Kafka)로 전환되어, 테스트 데이터 셋업은 서비스 직접 호출
            var coupon1 = couponRepository.findById(couponId1).orElseThrow();
            var coupon2Entity = couponRepository.findById(couponId2).orElseThrow();
            ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon1, 1L));
            ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon2Entity, 1L));

            var coupon2 = couponRepository.findById(couponId2).orElseThrow();
            ReflectionTestUtils.setField(coupon2, "expiredAt", ZonedDateTime.now().minusDays(1));
            couponRepository.save(coupon2);

            // act
            var response = getMyOwnedCoupons(testRestTemplate, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body = response.getBody().data();
            assertAll(
                    () -> assertThat(body.content()).hasSize(2),
                    () -> assertThat(body.content().get(0).status()).isEqualTo("EXPIRED"),
                    () -> assertThat(body.content().get(1).status()).isEqualTo("AVAILABLE"),
                    () -> assertThat(body.hasNext()).isFalse()
            );
        }

        @DisplayName("보유한 쿠폰이 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoCouponsOwned() {
            // act
            var response = getMyOwnedCoupons(testRestTemplate, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body = response.getBody().data();
            assertAll(
                    () -> assertThat(body.content()).isEmpty(),
                    () -> assertThat(body.hasNext()).isFalse()
            );
        }

        @DisplayName("인증되지 않은 사용자가 조회하면, 401 응답을 받는다.")
        @Test
        void returns401_whenNotAuthenticated() {
            // act
            var response = getMyOwnedCoupons(testRestTemplate, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
