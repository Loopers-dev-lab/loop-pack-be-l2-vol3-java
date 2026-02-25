package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api-admin/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        ProductJpaRepository productJpaRepository,
        BrandJpaRepository brandJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productJpaRepository = productJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api-admin/v1/products - 상품 목록 조회")
    @Nested
    class GetAll {

        @DisplayName("상품이 존재하면, 상품 목록을 반환한다.")
        @Test
        void returnsProductList_whenProductsExist() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            productJpaRepository.save(new ProductModel(brand, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE));

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "?page=0&size=20",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(2)
            );
        }

        @DisplayName("삭제된 상품은 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedProducts() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            ProductModel deletedProduct = new ProductModel(brand, "삭제상품", 100000L, "삭제될 상품", 10, ProductStatus.ON_SALE);
            deletedProduct.delete();
            productJpaRepository.save(deletedProduct);

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "?page=0&size=20",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId} - 상품 상세 조회")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID가 주어지면, 상품 상세 정보를 반환한다.")
        @Test
        void returnsProduct_whenIdExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));

            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId(),
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(150000L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/999",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api-admin/v1/products - 상품 등록")
    @Nested
    class Register {

        @DisplayName("정상적인 정보가 주어지면, 상품이 등록된다.")
        @Test
        void registersProduct_whenValidInfoIsProvided() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            RegisterRequest request = new RegisterRequest(brand.getId(), "에어맥스", 150000L, "나이키 에어맥스", 100, "ON_SALE");

            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // arrange
            RegisterRequest request = new RegisterRequest(999L, "에어맥스", 150000L, "나이키 에어맥스", 100, "ON_SALE");

            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId} - 상품 수정")
    @Nested
    class Update {

        @DisplayName("정상적인 정보가 주어지면, 상품이 수정된다.")
        @Test
        void updatesProduct_whenValidInfoIsProvided() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            UpdateRequest request = new UpdateRequest(brand.getId(), "에어포스", 120000L, "나이키 에어포스", 50, "ON_SALE");

            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어포스"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(120000L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            UpdateRequest request = new UpdateRequest(brand.getId(), "에어포스", 120000L, "나이키 에어포스", 50, "ON_SALE");

            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/999",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId} - 상품 삭제")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품 ID가 주어지면, 상품이 삭제된다.")
        @Test
        void deletesProduct_whenIdExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId(),
                HttpMethod.DELETE,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // verify soft delete - product should not be found via API
            ParameterizedTypeReference<ApiResponse<ProductResponse>> getResponseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> getResponse = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId(),
                HttpMethod.GET,
                null,
                getResponseType
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/999",
                HttpMethod.DELETE,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    record RegisterRequest(
        Long brandId,
        String name,
        Long price,
        String description,
        Integer stockQuantity,
        String status
    ) {}

    record UpdateRequest(
        Long brandId,
        String name,
        Long price,
        String description,
        Integer stockQuantity,
        String status
    ) {}

    record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        String description,
        int stockQuantity,
        String status,
        long likeCount,
        String createdAt,
        String updatedAt
    ) {}
}
