package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(
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

    private Brand saveBrand(String name) {
        return brandJpaRepository.save(Brand.create(name, null));
    }

    private Product saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(Product.create(brandId, name, null, price, stock));
    }

    @DisplayName("상품 상세 조회 시")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 VISIBLE 상품을 조회하면, 200 OK와 상품 정보를 반환한다.")
        @Test
        void returnsOk_whenProductExists() {
            // arrange
            String productName = "에어맥스";
            int productPrice = 150000;
            Brand brand = saveBrand("TEST_BRAND");
            Product saved = saveProduct(brand.getId(), productName, productPrice, 10);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(productName),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(productPrice)
            );
        }

        @DisplayName("상품 조회 시 likeCount가 포함되어 반환된다")
        @Test
        void returnsOk_withLikeCountDefault() {
            // arrange
            Brand brand = saveBrand("TEST_BRAND");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().likeCount()).isGreaterThan(0)
            );
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/99999",
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 상품을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductIsDeleted() {
            // arrange
            Brand brand = saveBrand("TEST_BRAND");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            saved.delete();
            productJpaRepository.save(saved);

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("HIDDEN 상품을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductIsHidden() {
            // arrange
            Brand brand = saveBrand("TEST_BRAND");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            saved.changeVisibility(Product.Visibility.HIDDEN);
            productJpaRepository.save(saved);

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록 조회 시")
    @Nested
    class GetProducts {

        @DisplayName("파라미터 없이 조회하면, 200 OK와 상품 목록을 반환한다.")
        @Test
        void returnsOk_withDefaultParams() {
            // arrange
            Brand brand = saveBrand("TEST_BRAND");
            Product productA = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            Product productB = saveProduct(brand.getId(), "조던", 200000, 5);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(productA.getId(), productB.getId())
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

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?brandId=" + nike.getId(),
                            HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                    );

            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(nikeProduct.getId()),
                    () -> assertThat(ids).doesNotContain(adidasProduct.getId())
            );
        }

        @DisplayName("sort=PRICE_ASC로 조회하면, 가격 낮은순으로 반환한다.")
        @Test
        void returnsSortedByPriceAsc() {
            // arrange
            Brand brand = saveBrand("TEST_BRAND");
            Product expensiveProduct = saveProduct(brand.getId(), "비싼신발", 300000, 5);
            Product cheapProduct = saveProduct(brand.getId(), "싼신발", 50000, 10);
            Product middleProduct = saveProduct(brand.getId(), "중간신발", 150000, 7);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?sort=PRICE_ASC",
                            HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                    );

            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids.indexOf(cheapProduct.getId()))
                            .isLessThan(ids.indexOf(middleProduct.getId()))
                            .isLessThan(ids.indexOf(expensiveProduct.getId()))
            );
        }

        @DisplayName("삭제된 상품은 목록에서 제외된다.")
        @Test
        void excludesDeletedProducts() {
            // arrange
            Brand brand = saveBrand("TEST_BRAND");
            Product visibleProduct = saveProduct(brand.getId(), "정상상품", 100000, 10);
            Product deletedProduct = saveProduct(brand.getId(), "삭제상품", 50000, 5);
            deletedProduct.delete();
            productJpaRepository.save(deletedProduct);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(visibleProduct.getId()),
                    () -> assertThat(ids).doesNotContain(deletedProduct.getId())
            );
        }

        @DisplayName("HIDDEN 상품은 목록에서 제외된다.")
        @Test
        void excludesHiddenProducts() {
            // arrange
            Brand brand = saveBrand("TEST_BRAND");
            Product visibleProduct1 = saveProduct(brand.getId(), "노출상품1", 100000, 10);
            Product visibleProduct2 = saveProduct(brand.getId(), "노출상품2", 200000, 10);
            Product hiddenProduct = saveProduct(brand.getId(), "숨김상품", 50000, 5);
            hiddenProduct.changeVisibility(Product.Visibility.HIDDEN);
            productJpaRepository.save(hiddenProduct);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(visibleProduct1.getId(), visibleProduct2.getId()),
                    () -> assertThat(ids).doesNotContain(hiddenProduct.getId())
            );
        }
    }
}
