package com.loopers.support;

import com.loopers.interfaces.api.brand.BrandRequest;
import com.loopers.interfaces.api.coupon.CouponAdminV1Dto;
import com.loopers.interfaces.api.coupon.CouponRequest;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.product.ProductRequest;
import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class E2ETestFixture {

    private static final String BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String COUPON_ENDPOINT = "/api-admin/v1/coupons";
    private static final String COUPON_USER_ENDPOINT = "/api/v1/coupons";
    private static final String PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String USER_ENDPOINT = "/api/v1/users";

    @Autowired
    private TestRestTemplate restTemplate;

    // Auth

    public HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "admin-ldap");
        return headers;
    }

    public HttpHeaders userHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    // Setup

    public void signUp(String loginId, String password, String name, String email) {
        UserRequest.SignUp request = new UserRequest.SignUp(
                loginId, password, name,
                LocalDate.of(2000, 1, 15), email
        );
        restTemplate.exchange(
                USER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
    }

    public Long registerBrand(String name, String description) {
        BrandRequest.Register request = new BrandRequest.Register(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = restTemplate.exchange(
                BRAND_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    public Long registerCoupon(String name, String type, int value,
                               BigDecimal minOrderAmount, int maxIssueCount, LocalDateTime expiredAt) {
        CouponRequest.Register request = new CouponRequest.Register(
                name, type, value, minOrderAmount, maxIssueCount, expiredAt
        );
        ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = restTemplate.exchange(
                COUPON_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    public Long issueCoupon(Long couponId, String loginId, String password) {
        ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> response = restTemplate.exchange(
                COUPON_USER_ENDPOINT + "/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(userHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    public Long registerProduct(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        ProductRequest.Register request = new ProductRequest.Register(
                brandId, name, price, stockQuantity, description
        );
        ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = restTemplate.exchange(
                PRODUCT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    public void updateProduct(Long productId, String name, BigDecimal price,
                              Integer stockQuantity, String description) {
        ProductRequest.UpdateInfo request = new ProductRequest.UpdateInfo(
                name, price, stockQuantity, description
        );
        restTemplate.exchange(
                PRODUCT_ENDPOINT + "/" + productId, HttpMethod.PATCH,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>>() {}
        );
    }

    // Teardown

    public void deleteBrand(Long brandId) {
        restTemplate.exchange(
                BRAND_ENDPOINT + "/" + brandId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    public void deleteCoupon(Long couponId) {
        restTemplate.exchange(
                COUPON_ENDPOINT + "/" + couponId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    public void deleteProduct(Long productId) {
        restTemplate.exchange(
                PRODUCT_ENDPOINT + "/" + productId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }
}
