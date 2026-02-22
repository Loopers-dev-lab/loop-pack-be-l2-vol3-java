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
    private static final String PRODUCT_ENDPOINT = "/api/v1/products";

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

    public static ResponseEntity<ApiResponse<Object>> updateProduct(
            TestRestTemplate testRestTemplate,
            Long productId,
            ProductDto.UpdateProductRequest request,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                PRODUCT_ADMIN_ENDPOINT + "/" + productId,
                HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<Object>> updateProduct(
            TestRestTemplate testRestTemplate,
            Long productId,
            ProductDto.UpdateProductRequest request
    ) {
        return updateProduct(testRestTemplate, productId, request, adminAuthHeaders());
    }

    public static ResponseEntity<ApiResponse<Object>> deleteProduct(
            TestRestTemplate testRestTemplate,
            Long productId,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                PRODUCT_ADMIN_ENDPOINT + "/" + productId,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<Object>> deleteProduct(
            TestRestTemplate testRestTemplate,
            Long productId
    ) {
        return deleteProduct(testRestTemplate, productId, adminAuthHeaders());
    }

    public static ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            TestRestTemplate testRestTemplate,
            Long productId
    ) {
        ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                PRODUCT_ADMIN_ENDPOINT + "/" + productId,
                HttpMethod.GET,
                new HttpEntity<>(adminAuthHeaders()),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<ProductDto.ProductDetailResponse>> getActiveProduct(
            TestRestTemplate testRestTemplate,
            Long productId,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<ProductDto.ProductDetailResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                PRODUCT_ENDPOINT + "/" + productId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<ProductDto.ProductDetailResponse>> getActiveProduct(
            TestRestTemplate testRestTemplate,
            Long productId
    ) {
        return getActiveProduct(testRestTemplate, productId, new HttpHeaders());
    }
}
