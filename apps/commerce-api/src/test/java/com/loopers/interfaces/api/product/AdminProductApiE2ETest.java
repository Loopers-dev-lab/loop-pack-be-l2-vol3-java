package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AdminProductApiE2ETest {

    private static final String ADMIN_PRODUCTS_URL = "/api-admin/v1/products";
    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String VALID_LDAP = "loopers.admin";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LDAP, VALID_LDAP);
        return headers;
    }

    private HttpEntity<Void> adminEntity() {
        return new HttpEntity<>(adminHeaders());
    }

    private <T> HttpEntity<T> adminEntity(T body) {
        return new HttpEntity<>(body, adminHeaders());
    }

    private Brand createActiveBrand(String name) {
        return brandRepository.save(Brand.register(name, name + " 설명"));
    }

    private Product createProductWithInventory(Long brandId, String name, int price, int quantity) {
        Product product = productRepository.save(Product.register(brandId, name, name + " 설명", price));
        inventoryRepository.save(Inventory.initialize(product.getId(), quantity));
        return product;
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class 상품_등록 {

        @Test
        void 유효한_정보면_201_Created를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            AdminProductRequest.CreateProductRequest request =
                    new AdminProductRequest.CreateProductRequest(
                            brand.getId(), "에어맥스", "나이키 에어맥스", 150000, 100);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL, HttpMethod.POST, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        void 비활성_브랜드면_400_Bad_Request를_반환한다() {
            // arrange
            Brand brand = Brand.register("비활성", "설명");
            brand.changeStatus(BrandStatus.INACTIVE);
            Brand saved = brandRepository.save(brand);

            AdminProductRequest.CreateProductRequest request =
                    new AdminProductRequest.CreateProductRequest(
                            saved.getId(), "에어맥스", "나이키 에어맥스", 150000, 100);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL, HttpMethod.POST, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class 상품_목록_조회 {

        @Test
        void 전체_상품이_조회되고_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            createProductWithInventory(brand.getId(), "상품1", 10000, 100);
            createProductWithInventory(brand.getId(), "상품2", 20000, 50);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL, HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 페이지네이션_파라미터로_조회되고_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            createProductWithInventory(brand.getId(), "상품1", 10000, 100);
            createProductWithInventory(brand.getId(), "상품2", 20000, 50);
            createProductWithInventory(brand.getId(), "상품3", 30000, 30);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "?page=0&size=2", HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class 상품_상세_조회 {

        @Test
        void 존재하는_상품이면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProductWithInventory(brand.getId(), "에어맥스", 150000, 100);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/" + product.getId(),
                    HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_ID면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/999",
                    HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api-admin/v1/products/{productId}")
    @Nested
    class 상품_수정 {

        @Test
        void 유효한_정보면_200_OK를_반환하고_실제로_수정된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProductWithInventory(brand.getId(), "에어맥스", 150000, 100);
            AdminProductRequest.UpdateProductRequest request =
                    new AdminProductRequest.UpdateProductRequest("에어포스", "나이키 에어포스", 120000);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/" + product.getId(),
                    HttpMethod.PATCH, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("에어포스");
            assertThat(updated.getBasePrice()).isEqualTo(120000);
        }
    }

    @DisplayName("PATCH /api-admin/v1/products/{productId}/status")
    @Nested
    class 상품_상태_변경 {

        @Test
        void SOLDOUT으로_변경하면_200_OK를_반환하고_실제로_변경된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProductWithInventory(brand.getId(), "에어맥스", 150000, 100);
            AdminProductRequest.ChangeStatusRequest request =
                    new AdminProductRequest.ChangeStatusRequest(ProductStatus.SOLDOUT);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/" + product.getId() + "/status",
                    HttpMethod.PATCH, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Product changed = productRepository.findById(product.getId()).orElseThrow();
            assertThat(changed.getStatus()).isEqualTo(ProductStatus.SOLDOUT);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class 상품_삭제 {

        @Test
        void 존재하는_상품이면_200_OK를_반환하고_상품과_재고가_소프트_삭제된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProductWithInventory(brand.getId(), "에어맥스", 150000, 100);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/" + product.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 상품 소프트 삭제 검증
            Product deleted = productRepository.findById(product.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();

            // 재고 소프트 삭제 검증 (findByProductId는 soft delete 필터 적용)
            assertThat(inventoryRepository.findByProductId(product.getId())).isEmpty();
        }

        @Test
        void 존재하지_않는_ID면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/999",
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 이미_삭제된_상품이면_409_Conflict를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProductWithInventory(brand.getId(), "에어맥스", 150000, 100);

            // 1차 삭제
            testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/" + product.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // act - 2차 삭제
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/" + product.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}
