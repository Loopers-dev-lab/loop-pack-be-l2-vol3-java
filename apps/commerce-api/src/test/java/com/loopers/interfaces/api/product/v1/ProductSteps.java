package com.loopers.interfaces.api.product.v1;

import static com.loopers.support.E2ETestHelper.adminAuthHeaders;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.product.v1.ProductDto.ProductResponse;

public class ProductSteps {

    private static final String PRODUCT_ADMIN_ENDPOINT = "/api-admin/v1/products";

    public static ResponseEntity<ApiResponse<ProductDto.CreateProductResponse>> createProduct(
            TestRestTemplate testRestTemplate,
            ProductDto.CreateProductRequest request,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<ProductDto.CreateProductResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                PRODUCT_ADMIN_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<ProductDto.CreateProductResponse>> createProduct(
            TestRestTemplate testRestTemplate,
            ProductDto.CreateProductRequest request
    ) {
        return createProduct(testRestTemplate, request, adminAuthHeaders());
    }

    public static ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            TestRestTemplate testRestTemplate,
            String url
    ) {
        ParameterizedTypeReference<ApiResponse<PageResponse<ProductResponse>>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(adminAuthHeaders()),
                responseType
        );
    }
}