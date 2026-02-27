package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.product.dto.ProductV1Dto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/products";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String LDAP_VALUE = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminProductV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LDAP_HEADER, LDAP_VALUE);
        return headers;
    }

    private Brand saveBrand(String name) {
        return brandJpaRepository.save(Brand.create(name, null));
    }

    private Product saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(Product.create(brandId, name, null, price, stock));
    }

    @DisplayName("상품 등록 시")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 요청이면, 201 Created와 등록된 상품을 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            String productName = "에어맥스";
            int productPrice = 150000;
            Brand brand = saveBrand("나이키");
            ProductV1Dto.CreateRequest request = new ProductV1Dto.CreateRequest(
                    brand.getId(), productName, "클래식 러닝화", productPrice, 10
            );
            HttpEntity<ProductV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(productName),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(productPrice),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brand.getId())
            );
        }

        @DisplayName("name이 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsMissing() {
            // arrange
            Brand brand = saveBrand("나이키");
            ProductV1Dto.CreateRequest request = new ProductV1Dto.CreateRequest(
                    brand.getId(), null, null, 150000, 10
            );
            HttpEntity<ProductV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("price가 0 이하이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenPriceIsZeroOrLess() {
            // arrange
            Brand brand = saveBrand("나이키");
            ProductV1Dto.CreateRequest request = new ProductV1Dto.CreateRequest(
                    brand.getId(), "에어맥스", null, 0, 10
            );
            HttpEntity<ProductV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("stockQuantity가 음수이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenStockQuantityIsNegative() {
            // arrange
            Brand brand = saveBrand("나이키");
            ProductV1Dto.CreateRequest request = new ProductV1Dto.CreateRequest(
                    brand.getId(), "에어맥스", null, 150000, -1
            );
            HttpEntity<ProductV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 브랜드에 등록하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // arrange
            ProductV1Dto.CreateRequest request = new ProductV1Dto.CreateRequest(
                    999L, "에어맥스", null, 150000, 10
            );
            HttpEntity<ProductV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드에 등록하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandIsDeleted() {
            // arrange
            Brand brand = saveBrand("나이키");
            brand.delete();
            brandJpaRepository.save(brand);

            ProductV1Dto.CreateRequest request = new ProductV1Dto.CreateRequest(
                    brand.getId(), "에어맥스", null, 150000, 10
            );
            HttpEntity<ProductV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("상품 상세 조회 시")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품을 조회하면, 200 OK와 상품 정보를 반환한다.")
        @Test
        void returnsOk_whenProductExists() {
            // arrange
            String productName = "에어맥스";
            Brand brand = saveBrand("나이키");
            Product saved = saveProduct(brand.getId(), productName, 150000, 10);
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, entity,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(productName)
            );
        }

        @DisplayName("어드민은 HIDDEN 상품도 조회할 수 있다.")
        @Test
        void returnsOk_whenProductIsHidden() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product saved = saveProduct(brand.getId(), "숨김상품", 150000, 10);
            saved.changeVisibility(Product.Visibility.HIDDEN);
            productJpaRepository.save(saved);
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, entity,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/999",
                            HttpMethod.GET, entity,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록 조회 시")
    @Nested
    class GetProducts {

        @DisplayName("등록된 상품 목록을 페이지 단위로 반환한다.")
        @Test
        void returnsPagedProducts() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product productA = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            Product productB = saveProduct(brand.getId(), "조던", 200000, 5);
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?page=0&size=20",
                            HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.AdminProductResponse::id)
                                     .toList();

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(productA.getId(), productB.getId())
            );
        }

        @DisplayName("삭제된 상품은 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedProducts() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product active = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            Product deleted = saveProduct(brand.getId(), "조던", 200000, 5);
            deleted.delete();
            productJpaRepository.save(deleted);
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?page=0&size=20",
                            HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.AdminProductResponse::id)
                                     .toList();

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(active.getId()),
                    () -> assertThat(ids).doesNotContain(deleted.getId())
            );
        }

        @DisplayName("brandId로 필터링하면, 해당 브랜드 상품만 반환한다.")
        @Test
        void returnsFilteredByBrandId() {
            // arrange
            Brand nike = saveBrand("나이키");
            Brand adidas = saveBrand("아디다스");
            Product nikeProduct = saveProduct(nike.getId(), "에어맥스", 150000, 10);
            Product adidasProduct = saveProduct(adidas.getId(), "슈퍼스타", 120000, 8);
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?brandId=" + nike.getId(),
                            HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.AdminProductResponse::id)
                                     .toList();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(nikeProduct.getId()),
                    () -> assertThat(ids).doesNotContain(adidasProduct.getId())
            );
        }
    }

    @DisplayName("상품 수정 시")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 요청이면, 200 OK와 수정된 상품을 반환한다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            String updatedName = "에어맥스 v2";
            int updatedPrice = 180000;
            Brand brand = saveBrand("나이키");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            ProductV1Dto.UpdateRequest request = new ProductV1Dto.UpdateRequest(
                    updatedName, "업데이트 버전", updatedPrice, 20, Product.Visibility.VISIBLE
            );
            HttpEntity<ProductV1Dto.UpdateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.PUT, entity,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(updatedName),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(updatedPrice)
            );
        }

        @DisplayName("존재하지 않는 상품을 수정하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            ProductV1Dto.UpdateRequest request = new ProductV1Dto.UpdateRequest(
                    "에어맥스 v2", null, 180000, 20, Product.Visibility.VISIBLE
            );
            HttpEntity<ProductV1Dto.UpdateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/999",
                            HttpMethod.PUT, entity,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("상품 삭제 시")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 상품을 삭제하면, 200 OK를 반환하고 soft delete 처리된다.")
        @Test
        void returnsOk_andSoftDeletes() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.DELETE, entity,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(productJpaRepository.findById(saved.getId()).orElseThrow().getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 상품을 삭제하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/999",
                            HttpMethod.DELETE, entity,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
