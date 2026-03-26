package com.loopers.interfaces.api.coupon.v1;

import static com.loopers.interfaces.api.coupon.v1.CouponSteps.createCoupon;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.deleteCoupon;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.issueCoupon;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.adminAuthHeaders;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.loopers.interfaces.api.coupon.v1.CouponDto.CreateCouponRequest;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class CouponV1ApiE2ETest extends BaseE2ETest {

    @Autowired
    private CouponRepository couponRepository;

    private HttpHeaders userHeaders;

    @BeforeEach
    void setUp() {
        var signUpRequest = new UserV1Dto.SignUpRequest(
                "testuser1", "Password1!", "홍길동", "1990-01-15", "test@example.com"
        );
        signUp(testRestTemplate, signUpRequest);
        userHeaders = userAuthHeaders(signUpRequest.loginId(), signUpRequest.password());
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 쿠폰을 발급하면, 201 응답을 받는다.")
        @Test
        void returns201_whenValidCouponIssued() {
            // arrange
            var request = new CreateCouponRequest(
                    "테스트 쿠폰",
                    CouponType.FIXED,
                    5000L,
                    null,
                    10000L,
                    ZonedDateTime.now().plusDays(30),
                    10000
            );
            var couponId = createCoupon(testRestTemplate, request);

            // act
            var response = issueCoupon(testRestTemplate, couponId, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하면, 404 응답을 받는다.")
        @Test
        void returns404_whenCouponNotFound() {
            // act
            var response = issueCoupon(testRestTemplate, 999L, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.COUPON_NOT_FOUND);
        }

        @DisplayName("삭제된 쿠폰을 발급하면, 404 응답을 받는다.")
        @Test
        void returns404_whenCouponIsDeleted() {
            // arrange
            var request = new CreateCouponRequest(
                    "테스트 쿠폰",
                    CouponType.FIXED,
                    5000L,
                    null,
                    10000L,
                    ZonedDateTime.now().plusDays(30),
                    10000
            );
            var couponId = createCoupon(testRestTemplate, request);
            deleteCoupon(testRestTemplate, couponId, adminAuthHeaders());

            // act
            var response = issueCoupon(testRestTemplate, couponId, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.COUPON_NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰을 발급하면, 400 응답을 받는다.")
        @Test
        void returns400_whenCouponIsExpired() {
            // arrange
            var request = new CreateCouponRequest(
                    "테스트 쿠폰",
                    CouponType.FIXED,
                    5000L,
                    null,
                    10000L,
                    ZonedDateTime.now().plusDays(30),
                    10000
            );
            var couponId = createCoupon(testRestTemplate, request);
            var coupon = couponRepository.findById(couponId).orElseThrow();
            ReflectionTestUtils.setField(coupon, "expiredAt", ZonedDateTime.now().minusDays(1));
            couponRepository.save(coupon);

            // act
            var response = issueCoupon(testRestTemplate, couponId, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.EXPIRED_COUPON);
        }

        @DisplayName("이미 발급받은 쿠폰을 중복 발급하면, 400 응답을 받는다.")
        @Test
        void returns400_whenDuplicateIssue() {
            // arrange
            var request = new CreateCouponRequest(
                    "테스트 쿠폰",
                    CouponType.FIXED,
                    5000L,
                    null,
                    10000L,
                    ZonedDateTime.now().plusDays(30),
                    10000
            );
            var couponId = createCoupon(testRestTemplate, request);
            issueCoupon(testRestTemplate, couponId, userHeaders);

            // act
            var response = issueCoupon(testRestTemplate, couponId, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.ALREADY_COUPON_ISSUED);
        }

        @DisplayName("인증되지 않은 사용자가 발급하면, 401 응답을 받는다.")
        @Test
        void returns401_whenNotAuthenticated() {
            // arrange
            var request = new CreateCouponRequest(
                    "테스트 쿠폰",
                    CouponType.FIXED,
                    5000L,
                    null,
                    10000L,
                    ZonedDateTime.now().plusDays(30),
                    10000
            );
            var couponId = createCoupon(testRestTemplate, request);

            // act
            var response = issueCoupon(testRestTemplate, couponId, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
