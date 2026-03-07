package com.loopers.interfaces.api.brand;

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
class AdminBrandApiE2ETest {

    private static final String ADMIN_BRANDS_URL = "/api-admin/v1/brands";
    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String VALID_LDAP = "loopers.admin";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

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

    private Product createProductWithInventory(Long brandId, String name, ProductStatus status) {
        Product product = Product.register(brandId, name, name + " 설명", 10000);
        if (status != ProductStatus.ACTIVE) {
            product.changeStatus(status);
        }
        Product saved = productRepository.save(product);
        inventoryRepository.save(Inventory.initialize(saved.getId(), 100));
        return saved;
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class 브랜드_등록 {

        @Test
        void 유효한_정보면_201_Created를_반환한다() {
            // arrange
            AdminBrandRequest.CreateBrandRequest request =
                    new AdminBrandRequest.CreateBrandRequest("나이키", "스포츠 브랜드");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL, HttpMethod.POST, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class 브랜드_목록_조회 {

        @Test
        void 전체_브랜드가_조회되고_200_OK를_반환한다() {
            // arrange
            brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));
            Brand inactive = Brand.register("비활성", "설명");
            inactive.changeStatus(BrandStatus.INACTIVE);
            brandRepository.save(inactive);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL, HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 페이지네이션_파라미터로_조회되고_200_OK를_반환한다() {
            // arrange
            brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));
            brandRepository.save(Brand.register("아디다스", "독일 브랜드"));
            brandRepository.save(Brand.register("뉴발란스", "미국 브랜드"));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "?page=0&size=2", HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class 브랜드_상세_조회 {

        @Test
        void 존재하는_브랜드면_200_OK를_반환한다() {
            // arrange
            Brand brand = brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId(), HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 브랜드_상세에_전체_상품_목록이_포함된다() {
            // arrange
            Brand brand = brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));
            createProductWithInventory(brand.getId(), "에어맥스", ProductStatus.ACTIVE);
            createProductWithInventory(brand.getId(), "숨김상품", ProductStatus.HIDDEN);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId(), HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert - 어드민은 전체 상품 목록 (ACTIVE + HIDDEN)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_ID면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/999", HttpMethod.GET, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api-admin/v1/brands/{brandId}")
    @Nested
    class 브랜드_수정 {

        @Test
        void 유효한_정보면_200_OK를_반환하고_실제로_수정된다() {
            // arrange
            Brand brand = brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));
            AdminBrandRequest.UpdateBrandRequest request =
                    new AdminBrandRequest.UpdateBrandRequest("아디다스", "독일 브랜드");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId(),
                    HttpMethod.PATCH, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Brand updated = brandRepository.findById(brand.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("아디다스");
            assertThat(updated.getDescription()).isEqualTo("독일 브랜드");
        }

        @Test
        void 존재하지_않는_ID면_404_Not_Found를_반환한다() {
            // arrange
            AdminBrandRequest.UpdateBrandRequest request =
                    new AdminBrandRequest.UpdateBrandRequest("아디다스", "독일 브랜드");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/999",
                    HttpMethod.PATCH, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api-admin/v1/brands/{brandId}/status")
    @Nested
    class 브랜드_상태_변경 {

        @Test
        void INACTIVE로_변경하면_200_OK를_반환하고_실제로_변경된다() {
            // arrange
            Brand brand = brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));
            AdminBrandRequest.ChangeStatusRequest request =
                    new AdminBrandRequest.ChangeStatusRequest(BrandStatus.INACTIVE);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId() + "/status",
                    HttpMethod.PATCH, adminEntity(request), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Brand changed = brandRepository.findById(brand.getId()).orElseThrow();
            assertThat(changed.getStatus()).isEqualTo(BrandStatus.INACTIVE);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class 브랜드_삭제 {

        @Test
        void 존재하는_브랜드면_200_OK를_반환하고_실제로_삭제된다() {
            // arrange
            Brand brand = brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Brand deleted = brandRepository.findById(brand.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @Test
        void 브랜드_삭제_시_소속_상품과_재고가_연쇄_삭제된다() {
            // arrange
            Brand brand = brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));
            Product product = createProductWithInventory(brand.getId(), "에어맥스", ProductStatus.ACTIVE);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 브랜드 소프트 삭제 검증
            Brand deleted = brandRepository.findById(brand.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();

            // 상품 소프트 삭제 검증
            Product deletedProduct = productRepository.findById(product.getId()).orElseThrow();
            assertThat(deletedProduct.getDeletedAt()).isNotNull();

            // 재고 소프트 삭제 검증
            assertThat(inventoryRepository.findByProductId(product.getId())).isEmpty();
        }

        @Test
        void 삭제_후_다시_삭제하면_409_Conflict를_반환한다() {
            // arrange
            Brand brand = brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));

            // 1차 삭제
            testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // act - 2차 삭제
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + brand.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 이미_삭제된_브랜드면_409_Conflict를_반환한다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");
            brand.discontinue();
            Brand saved = brandRepository.save(brand);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/" + saved.getId(),
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 존재하지_않는_ID면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_URL + "/999",
                    HttpMethod.DELETE, adminEntity(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
