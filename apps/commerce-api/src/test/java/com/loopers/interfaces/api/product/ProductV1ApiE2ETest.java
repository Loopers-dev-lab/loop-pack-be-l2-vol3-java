package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String VALID_PRODUCT_NAME = "나이키 에어맥스";
    private static final int VALID_PRICE = 10000;
    private static final int VALID_STOCK = 100;
    private static final Long NOT_EXISTED_PRODUCT_ID = 999L;

    // BR-A03: 상품 조회는 공개 API - auth 헤더 불필요
    private static final String ENDPOINT_GET_LIST = "/api/v1/products";
    private static final Function<Long, String> ENDPOINT_PRODUCT = id -> "/api/v1/products/" + id;

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    @Autowired
    public ProductV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("인증 없이 상품 목록을 조회하면, 200 OK와 상품 목록을 반환한다.")
        @Test
        void returnsProductList_withoutAuth() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_LIST, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().products().get(0).name()).isEqualTo(VALID_PRODUCT_NAME)
            );
        }

        @DisplayName("허용되지 않는 sort 값으로 요청하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenSortIsInvalid() {
            // arrange
            String requestUrl = ENDPOINT_GET_LIST + "?sort=invalid";

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.BAD_REQUEST.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api/v1/products/{id}")
    @Nested
    class GetProductDetails {

        @DisplayName("인증 없이 존재하는 productId로 요청하면, 200 OK와 상품 정보를 반환한다.")
        @Test
        void returnsProductDetails_withoutAuth() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            String requestUrl = ENDPOINT_PRODUCT.apply(product.getId());

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(VALID_PRODUCT_NAME),
                    () -> assertThat(response.getBody().data().inStock()).isTrue()
            );
        }

        @DisplayName("존재하지 않는 productId로 요청하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductIdDoesNotExist() {
            // arrange
            String requestUrl = ENDPOINT_PRODUCT.apply(NOT_EXISTED_PRODUCT_ID);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
