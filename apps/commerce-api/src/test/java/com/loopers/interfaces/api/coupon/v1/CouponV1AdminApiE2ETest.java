package com.loopers.interfaces.api.coupon.v1;

import static com.loopers.interfaces.api.coupon.v1.CouponSteps.createCoupon;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.getCoupon;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.getCoupons;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.updateCoupon;
import static com.loopers.support.E2ETestHelper.adminAuthHeaders;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.coupon.v1.CouponDto.CouponResponse;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class CouponV1AdminApiE2ETest extends BaseE2ETest {

    private static final String COUPON_ADMIN_ENDPOINT = "/api-admin/v1/coupons";
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @Autowired
    private CouponRepository couponRepository;

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class RegisterCoupon {

        @DisplayName("유효한 정액 쿠폰 정보를 입력하면, 쿠폰 생성에 성공한다.")
        @Test
        void createsFixedCoupon_whenValidInputProvided() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "여름 할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().couponId()).isNotNull()
            );
        }

        @DisplayName("유효한 정률 쿠폰 정보를 입력하면, 쿠폰 생성에 성공한다.")
        @Test
        void createsRateCoupon_whenValidInputProvided() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "10% 할인 쿠폰", CouponType.RATE, 10L, 5000L, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().couponId()).isNotNull()
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "여름 할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-Ldap 헤더 값이 잘못되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderValueIsWrong() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "여름 할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );
            var headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong.value");

            // act
            var response = createCoupon(testRestTemplate, request, headers);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("쿠폰명이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰명 길이가 유효하지 않으면, INVALID_COUPON_NAME 에러 응답을 받는다.")
        @ParameterizedTest(name = "길이가 {0}인 쿠폰명")
        @ValueSource(ints = {1, 51})
        void returnsInvalidCouponName_whenNameLengthIsInvalid(int length) {
            // arrange
            var name = "a".repeat(length);
            var request = new CouponDto.CreateCouponRequest(
                    name, CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_COUPON_NAME);
        }

        @DisplayName("정액 할인값이 1 미만이면, INVALID_DISCOUNT_VALUE 에러 응답을 받는다.")
        @Test
        void returnsInvalidDiscountValue_whenFixedDiscountValueIsLessThanOne() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "쿠폰명입니다", CouponType.FIXED, 0L, null, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_DISCOUNT_VALUE);
        }

        @DisplayName("정률 할인값이 1 미만이면, INVALID_DISCOUNT_VALUE 에러 응답을 받는다.")
        @Test
        void returnsInvalidDiscountValue_whenRateDiscountValueIsLessThanOne() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "쿠폰명입니다", CouponType.RATE, 0L, 5000L, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_DISCOUNT_VALUE);
        }

        @DisplayName("정률 할인값이 100 초과이면, INVALID_RATE_DISCOUNT_VALUE 에러 응답을 받는다.")
        @Test
        void returnsInvalidRateDiscountValue_whenDiscountValueExceeds100() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "쿠폰명입니다", CouponType.RATE, 101L, 5000L, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_RATE_DISCOUNT_VALUE);
        }

        @DisplayName("정률 쿠폰의 최대 할인 금액이 없으면, REQUIRED_MAX_DISCOUNT_AMOUNT 에러 응답을 받는다.")
        @Test
        void returnsRequiredMaxDiscountAmount_whenRateCouponWithoutMaxDiscount() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "쿠폰명입니다", CouponType.RATE, 10L, null, 10000L, FUTURE
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.REQUIRED_MAX_DISCOUNT_AMOUNT);
        }

        @DisplayName("만료일이 과거이면, INVALID_EXPIRED_AT 에러 응답을 받는다.")
        @Test
        void returnsInvalidExpiredAt_whenExpiredAtIsInThePast() {
            // arrange
            var pastDate = ZonedDateTime.now().minusDays(1);
            var request = new CouponDto.CreateCouponRequest(
                    "쿠폰명입니다", CouponType.FIXED, 5000L, null, 10000L, pastDate
            );

            // act
            var response = createCoupon(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_EXPIRED_AT);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class ReadCoupons {

        @DisplayName("삭제된 쿠폰을 포함한 쿠폰 목록이 생성일 내림차순으로 반환된다.")
        @Test
        void returnsCouponList_whenCouponsExist() {
            // arrange
            createCoupon(testRestTemplate,
                    new CouponDto.CreateCouponRequest("정액 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE),
                    adminAuthHeaders()
            );
            var coupon = couponRepository.findById(1L).orElseThrow();
            coupon.delete();
            couponRepository.save(coupon);

            createCoupon(testRestTemplate,
                    new CouponDto.CreateCouponRequest("정률 쿠폰", CouponType.RATE, 10L, 5000L, 10000L, FUTURE),
                    adminAuthHeaders()
            );

            var url = UriComponentsBuilder.fromPath(COUPON_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = getCoupons(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content()).extracting(CouponResponse::name)
                            .containsExactly("정률 쿠폰", "정액 쿠폰"),
                    () -> assertThat(response.getBody().data().content()).extracting(CouponResponse::createdAt)
                            .doesNotContainNull(),
                    () -> assertThat(response.getBody().data().content().get(1).deletedAt()).isNotNull()
            );
        }

        @DisplayName("쿠폰이 없으면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoCouponsExist() {
            // arrange
            var url = UriComponentsBuilder.fromPath(COUPON_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = getCoupons(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("페이지 크기보다 쿠폰이 많으면, hasNext가 true이다.")
        @Test
        void returnsHasNextTrue_whenMoreCouponsExist() {
            // arrange
            createCoupon(testRestTemplate,
                    new CouponDto.CreateCouponRequest("쿠폰1", CouponType.FIXED, 1000L, null, 5000L, FUTURE),
                    adminAuthHeaders()
            );
            createCoupon(testRestTemplate,
                    new CouponDto.CreateCouponRequest("쿠폰2", CouponType.FIXED, 2000L, null, 5000L, FUTURE),
                    adminAuthHeaders()
            );
            createCoupon(testRestTemplate,
                    new CouponDto.CreateCouponRequest("쿠폰3", CouponType.FIXED, 3000L, null, 5000L, FUTURE),
                    adminAuthHeaders()
            );

            var url = UriComponentsBuilder.fromPath(COUPON_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 2)
                    .toUriString();

            // act
            var response = getCoupons(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().hasNext()).isTrue()
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            var url = UriComponentsBuilder.fromPath(COUPON_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = getCoupons(testRestTemplate, url, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class ReadCouponDetail {

        @DisplayName("존재하는 쿠폰 ID로 조회하면, 쿠폰 상세 정보를 반환한다.")
        @Test
        void returnsCouponDetail_whenCouponExists() {
            // arrange
            var request = new CouponDto.CreateCouponRequest(
                    "여름 할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );
            var createResponse = createCoupon(testRestTemplate, request, adminAuthHeaders());
            var couponId = createResponse.getBody().data().couponId();

            // act
            var response = getCoupon(testRestTemplate, couponId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(couponId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("여름 할인 쿠폰"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(response.getBody().data().discountValue()).isEqualTo(5000L),
                    () -> assertThat(response.getBody().data().maxDiscountPrice()).isNull(),
                    () -> assertThat(response.getBody().data().minOrderPrice()).isEqualTo(10000L),
                    () -> assertThat(response.getBody().data().expiredAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().deletedAt()).isNull()
            );
        }

        @DisplayName("존재하지 않는 쿠폰 ID로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            var response = getCoupon(testRestTemplate, 999L);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.COUPON_NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateCoupon {

        @DisplayName("정액 쿠폰 정보를 수정하면, 수정에 성공한다.")
        @Test
        void updatesFixedCoupon_whenValidInputProvided() {
            // arrange
            var createRequest = new CouponDto.CreateCouponRequest(
                    "기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );
            var couponId = createCoupon(testRestTemplate, createRequest, adminAuthHeaders())
                    .getBody().data().couponId();

            var updateRequest = new CouponDto.UpdateCouponRequest(
                    "수정된 쿠폰", 3000L, null, 20000L, FUTURE
            );

            // act
            var response = updateCoupon(testRestTemplate, couponId, updateRequest, adminAuthHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var detail = getCoupon(testRestTemplate, couponId);
            assertAll(
                    () -> assertThat(detail.getBody().data().name()).isEqualTo("수정된 쿠폰"),
                    () -> assertThat(detail.getBody().data().discountValue()).isEqualTo(3000L),
                    () -> assertThat(detail.getBody().data().maxDiscountPrice()).isNull(),
                    () -> assertThat(detail.getBody().data().minOrderPrice()).isEqualTo(20000L),
                    () -> assertThat(detail.getBody().data().type()).isEqualTo(CouponType.FIXED)
            );
        }

        @DisplayName("정률 쿠폰 정보를 수정하면, 수정에 성공한다.")
        @Test
        void updatesRateCoupon_whenValidInputProvided() {
            // arrange
            var createRequest = new CouponDto.CreateCouponRequest(
                    "기존 쿠폰", CouponType.RATE, 10L, 5000L, 10000L, FUTURE
            );
            var couponId = createCoupon(testRestTemplate, createRequest, adminAuthHeaders())
                    .getBody().data().couponId();

            var updateRequest = new CouponDto.UpdateCouponRequest(
                    "수정된 쿠폰", 20L, 8000L, 15000L, FUTURE
            );

            // act
            var response = updateCoupon(testRestTemplate, couponId, updateRequest, adminAuthHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var detail = getCoupon(testRestTemplate, couponId);
            assertAll(
                    () -> assertThat(detail.getBody().data().name()).isEqualTo("수정된 쿠폰"),
                    () -> assertThat(detail.getBody().data().discountValue()).isEqualTo(20L),
                    () -> assertThat(detail.getBody().data().maxDiscountPrice()).isEqualTo(8000L),
                    () -> assertThat(detail.getBody().data().minOrderPrice()).isEqualTo(15000L),
                    () -> assertThat(detail.getBody().data().type()).isEqualTo(CouponType.RATE)
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            var updateRequest = new CouponDto.UpdateCouponRequest(
                    "수정 쿠폰", 3000L, null, 10000L, FUTURE
            );

            // act
            var response = updateCoupon(testRestTemplate, 1L, updateRequest, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 쿠폰을 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // arrange
            var updateRequest = new CouponDto.UpdateCouponRequest(
                    "수정 쿠폰", 3000L, null, 10000L, FUTURE
            );

            // act
            var response = updateCoupon(testRestTemplate, 999L, updateRequest, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.COUPON_NOT_FOUND);
        }

        @DisplayName("삭제된 쿠폰을 수정하면, 수정에 성공한다.")
        @Test
        void updatesDeletedCoupon_whenValidInputProvided() {
            // arrange
            var createRequest = new CouponDto.CreateCouponRequest(
                    "기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );
            var couponId = createCoupon(testRestTemplate, createRequest, adminAuthHeaders())
                    .getBody().data().couponId();

            var coupon = couponRepository.findById(couponId).orElseThrow();
            coupon.delete();
            couponRepository.save(coupon);

            var updateRequest = new CouponDto.UpdateCouponRequest(
                    "수정된 쿠폰", 3000L, null, 20000L, FUTURE
            );

            // act
            var response = updateCoupon(testRestTemplate, couponId, updateRequest, adminAuthHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var detail = getCoupon(testRestTemplate, couponId);
            assertThat(detail.getBody().data().name()).isEqualTo("수정된 쿠폰");
        }

        @DisplayName("쿠폰명이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            var createRequest = new CouponDto.CreateCouponRequest(
                    "기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, FUTURE
            );
            var couponId = createCoupon(testRestTemplate, createRequest, adminAuthHeaders())
                    .getBody().data().couponId();

            var updateRequest = new CouponDto.UpdateCouponRequest(
                    "", 3000L, null, 10000L, FUTURE
            );

            // act
            var response = updateCoupon(testRestTemplate, couponId, updateRequest, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }
    }
}
