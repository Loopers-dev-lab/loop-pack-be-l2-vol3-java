package com.loopers.interfaces.api.coupon.v1;

import static com.loopers.support.E2ETestHelper.adminAuthHeaders;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

public class CouponSteps {

    private static final String COUPON_ADMIN_ENDPOINT = "/api-admin/v1/coupons";
    private static final String COUPON_ENDPOINT = "/api/v1/coupons";

    public static ResponseEntity<ApiResponse<Void>> issueCoupon(
            TestRestTemplate testRestTemplate,
            Long couponId,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                COUPON_ENDPOINT + "/" + couponId + "/issue",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<CouponDto.CreateCouponResponse>> createCoupon(
            TestRestTemplate testRestTemplate,
            CouponDto.CreateCouponRequest request,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<CouponDto.CreateCouponResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                COUPON_ADMIN_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    public static Long createCoupon(
            TestRestTemplate testRestTemplate,
            CouponDto.CreateCouponRequest request
    ) {
        return createCoupon(testRestTemplate, request, adminAuthHeaders())
                .getBody().data().couponId();
    }

    public static ResponseEntity<ApiResponse<PageResponse<CouponDto.CouponResponse>>> getCoupons(
            TestRestTemplate testRestTemplate,
            String url,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<PageResponse<CouponDto.CouponResponse>>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<PageResponse<CouponDto.CouponResponse>>> getCoupons(
            TestRestTemplate testRestTemplate,
            String url
    ) {
        return getCoupons(testRestTemplate, url, adminAuthHeaders());
    }

    public static ResponseEntity<ApiResponse<CouponDto.CouponResponse>> getCoupon(
            TestRestTemplate testRestTemplate,
            Long couponId
    ) {
        return getCoupon(testRestTemplate, couponId, adminAuthHeaders());
    }

    public static ResponseEntity<ApiResponse<Void>> updateCoupon(
            TestRestTemplate testRestTemplate,
            Long couponId,
            CouponDto.UpdateCouponRequest request,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                COUPON_ADMIN_ENDPOINT + "/" + couponId,
                HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<CouponDto.CouponResponse>> getCoupon(
            TestRestTemplate testRestTemplate,
            Long couponId,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<CouponDto.CouponResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                COUPON_ADMIN_ENDPOINT + "/" + couponId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<Void>> deleteCoupon(
            TestRestTemplate testRestTemplate,
            Long couponId,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                COUPON_ADMIN_ENDPOINT + "/" + couponId,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                responseType
        );
    }
}
