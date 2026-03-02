package com.loopers.interfaces.api.admin;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductApiE2ETest {

    private static final String ADMIN_PRODUCTS_ENDPOINT = "/api/admin/v1/products";
    private static final String ADMIN_LDAP = "admin-test";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OptionRepository optionRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
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

    @Nested
    @DisplayName("POST /api/admin/v1/products")
    class CreateProductTest {

        @Test
        @DisplayName("상품을 생성할 수 있다")
        void create_success() {
            // arrange
            AdminProductDto.CreateRequest request = new AdminProductDto.CreateRequest(
                    testBrand.getId(), "신규 상품", BigDecimal.valueOf(15000));
            HttpEntity<AdminProductDto.CreateRequest> httpEntity = createAdminHttpEntity(request);

            // act
            ResponseEntity<ApiResponse<AdminProductDto.ProductResponse>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("신규 상품"),
                    () -> assertThat(response.getBody().data().basePrice()).isEqualByComparingTo(BigDecimal.valueOf(15000))
            );
        }

        @Test
        @DisplayName("Admin 인증 없이 요청하면 401 응답을 받는다")
        void create_unauthorized() {
            // arrange
            AdminProductDto.CreateRequest request = new AdminProductDto.CreateRequest(
                    testBrand.getId(), "상품", BigDecimal.valueOf(10000));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<AdminProductDto.CreateRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("POST /api/admin/v1/products/{productId}/options")
    class CreateOptionTest {

        @Test
        @DisplayName("상품에 옵션을 추가할 수 있다")
        void createOption_success() {
            // arrange
            Product product = productRepository.save(Product.create(testBrand.getId(), "상품", Money.of(BigDecimal.valueOf(10000))));
            AdminProductDto.CreateOptionRequest request = new AdminProductDto.CreateOptionRequest(
                    "L 사이즈", BigDecimal.valueOf(2000), 50);
            HttpEntity<AdminProductDto.CreateOptionRequest> httpEntity = createAdminHttpEntity(request);

            // act
            ResponseEntity<ApiResponse<AdminProductDto.OptionResponse>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT + "/" + product.getId() + "/options",
                    HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("L 사이즈"),
                    () -> assertThat(response.getBody().data().stock()).isEqualTo(50)
            );
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/v1/products/{id}")
    class UpdateProductTest {

        @Test
        @DisplayName("상품 정보를 수정할 수 있다")
        void update_success() {
            // arrange
            Product product = productRepository.save(Product.create(testBrand.getId(), "원래 상품", Money.of(BigDecimal.valueOf(10000))));
            AdminProductDto.UpdateRequest request = new AdminProductDto.UpdateRequest("수정된 상품", BigDecimal.valueOf(20000));
            HttpEntity<AdminProductDto.UpdateRequest> httpEntity = createAdminHttpEntity(request);

            // act
            ResponseEntity<ApiResponse<AdminProductDto.ProductResponse>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT + "/" + product.getId(),
                    HttpMethod.PUT, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("수정된 상품"),
                    () -> assertThat(response.getBody().data().basePrice()).isEqualByComparingTo(BigDecimal.valueOf(20000))
            );
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/v1/products/{id}")
    class DeleteProductTest {

        @Test
        @DisplayName("상품을 삭제하면 soft delete 된다")
        void delete_success() {
            // arrange
            Product product = productRepository.save(Product.create(testBrand.getId(), "삭제 상품", Money.of(BigDecimal.valueOf(10000))));
            optionRepository.save(Option.create(product.getId(), "옵션", Money.of(BigDecimal.ZERO), 10));
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT + "/" + product.getId(),
                    HttpMethod.DELETE, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Product deletedEntity = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(deletedEntity.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /api/admin/v1/products")
    class GetAllProductsTest {

        @Test
        @DisplayName("전체 상품 목록을 조회할 수 있다")
        void getAll_success() {
            // arrange
            productRepository.save(Product.create(testBrand.getId(), "상품A", Money.of(BigDecimal.valueOf(10000))));
            productRepository.save(Product.create(testBrand.getId(), "상품B", Money.of(BigDecimal.valueOf(20000))));
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<AdminProductDto.ProductListResponse>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT, HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().products()).hasSize(2)
            );
        }
    }

    @Nested
    @DisplayName("GET /api/admin/v1/products/{id}")
    class GetProductByIdTest {

        @Test
        @DisplayName("상품 상세를 옵션과 함께 조회할 수 있다")
        void getById_success() {
            // arrange
            Product product = productRepository.save(Product.create(testBrand.getId(), "상세 상품", Money.of(BigDecimal.valueOf(10000))));
            optionRepository.save(Option.create(product.getId(), "옵션A", Money.of(BigDecimal.ZERO), 10));
            optionRepository.save(Option.create(product.getId(), "옵션B", Money.of(BigDecimal.valueOf(1000)), 20));
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<AdminProductDto.ProductDetailResponse>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT + "/" + product.getId(),
                    HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().product().name()).isEqualTo("상세 상품"),
                    () -> assertThat(response.getBody().data().options()).hasSize(2)
            );
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/v1/products/{productId}/options/{optionId}/stock")
    class UpdateOptionStockTest {

        @Test
        @DisplayName("옵션의 재고를 수정할 수 있다")
        void updateStock_success() {
            // arrange
            Product product = productRepository.save(Product.create(testBrand.getId(), "상품", Money.of(BigDecimal.valueOf(10000))));
            Option option = optionRepository.save(Option.create(product.getId(), "옵션", Money.of(BigDecimal.ZERO), 10));
            AdminProductDto.UpdateStockRequest request = new AdminProductDto.UpdateStockRequest(200);
            HttpEntity<AdminProductDto.UpdateStockRequest> httpEntity = createAdminHttpEntity(request);

            // act
            ResponseEntity<ApiResponse<AdminProductDto.OptionResponse>> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_ENDPOINT + "/" + product.getId() + "/options/" + option.getId() + "/stock",
                    HttpMethod.PUT, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().stock()).isEqualTo(200)
            );
        }
    }

    private <T> HttpEntity<T> createAdminHttpEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-Ldap", ADMIN_LDAP);
        return new HttpEntity<>(body, headers);
    }
}
