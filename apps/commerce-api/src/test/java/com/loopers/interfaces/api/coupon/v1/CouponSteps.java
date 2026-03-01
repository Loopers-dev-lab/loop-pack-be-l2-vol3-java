package com.loopers.interfaces.api.coupon.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class CouponSteps {

    private static final String COUPON_ADMIN_ENDPOINT = "/api-admin/v1/coupons";

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
}
