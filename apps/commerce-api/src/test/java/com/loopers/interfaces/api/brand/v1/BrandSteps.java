package com.loopers.interfaces.api.brand.v1;

import static com.loopers.support.E2ETestHelper.adminAuthHeaders;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class BrandSteps {

    private static final String BRAND_ADMIN_ENDPOINT = "/api-admin/v1/brands";

    public static ResponseEntity<ApiResponse<BrandDto.CreateBrandResponse>> createBrand(
            TestRestTemplate testRestTemplate,
            BrandDto.CreateBrandRequest request,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<BrandDto.CreateBrandResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                BRAND_ADMIN_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    public static Long createBrand(
            TestRestTemplate testRestTemplate,
            BrandDto.CreateBrandRequest request
    ) {
        var result = createBrand(testRestTemplate, request, adminAuthHeaders());
        return result.getBody().data().brandId();
    }
}
