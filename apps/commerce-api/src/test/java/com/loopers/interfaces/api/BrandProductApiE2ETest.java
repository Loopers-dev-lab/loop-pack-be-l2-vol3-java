package com.loopers.interfaces.api;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandProductApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductAppService productAppService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Brand testBrand;

    @BeforeEach
    void setUp() {
        testBrand = brandRepository.save(Brand.create("테스트 브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/brands/{brandId}/products")
    @Nested
    class GetBrandProducts {

        @DisplayName("브랜드별 상품 목록을 조회할 수 있다.")
        @Test
        void getBrandProducts_success() {
            // arrange
            Product p1 = productRepository.save(Product.create(testBrand.getId(), "인기 상품", Money.of(BigDecimal.valueOf(10000))));
            Product p2 = productRepository.save(Product.create(testBrand.getId(), "보통 상품", Money.of(BigDecimal.valueOf(20000))));
            Product p3 = productRepository.save(Product.create(testBrand.getId(), "비인기 상품", Money.of(BigDecimal.valueOf(30000))));

            productAppService.increaseLikeCount(p1.getId());
            productAppService.increaseLikeCount(p1.getId());
            productAppService.increaseLikeCount(p1.getId());

            productAppService.increaseLikeCount(p2.getId());

            // act
            ResponseEntity<ApiResponse<ProductDto.BrandProductListResponse>> response = testRestTemplate.exchange(
                    "/api/v1/brands/" + testBrand.getId() + "/products?page=0",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().products()).hasSize(3),
                    () -> assertThat(response.getBody().data().products().get(0).productName()).isEqualTo("인기 상품"),
                    () -> assertThat(response.getBody().data().products().get(0).likeCount()).isEqualTo(3),
                    () -> assertThat(response.getBody().data().products().get(1).productName()).isEqualTo("보통 상품"),
                    () -> assertThat(response.getBody().data().products().get(2).productName()).isEqualTo("비인기 상품"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(20)
            );
        }

        @DisplayName("브랜드명이 응답 DTO에 포함된다.")
        @Test
        void getBrandProducts_includesBrandName() {
            // arrange
            productRepository.save(Product.create(testBrand.getId(), "상품", Money.of(BigDecimal.valueOf(10000))));

            // act
            ResponseEntity<ApiResponse<ProductDto.BrandProductListResponse>> response = testRestTemplate.exchange(
                    "/api/v1/brands/" + testBrand.getId() + "/products?page=0",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            List<ProductDto.BrandProductResponse> products = response.getBody().data().products();
            assertThat(products.get(0).brandName()).isEqualTo("테스트 브랜드");
        }

        @DisplayName("페이징이 정상 동작한다.")
        @Test
        void getBrandProducts_paging() {
            // arrange
            for (int i = 0; i < 25; i++) {
                productRepository.save(Product.create(testBrand.getId(), "상품" + i, Money.of(BigDecimal.valueOf(1000))));
            }

            // act
            ResponseEntity<ApiResponse<ProductDto.BrandProductListResponse>> response = testRestTemplate.exchange(
                    "/api/v1/brands/" + testBrand.getId() + "/products?page=1",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().products()).hasSize(5),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(25),
                    () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2)
            );
        }

        @DisplayName("존재하지 않는 브랜드 조회 시 404를 반환한다.")
        @Test
        void getBrandProducts_notFound() {
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    "/api/v1/brands/999999/products?page=0",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("허용 범위를 초과한 페이지 요청 시 400을 반환한다.")
        @Test
        void getBrandProducts_deepPaging_rejected() {
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    "/api/v1/brands/" + testBrand.getId() + "/products?page=51",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
