package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.like.ProductLikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class ProductLikeApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductLikeRepository productLikeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        UserRequest.SignupRequest signupRequest = new UserRequest.SignupRequest(
                "testuser", "Hx7!mK2@", "테스터", "1994-11-15", "test@example.com");
        testRestTemplate.postForEntity("/api/v1/users", signupRequest, ApiResponse.class);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        return headers;
    }

    private Brand createActiveBrand(String name) {
        return brandRepository.save(Brand.register(name, name + " 설명"));
    }

    private Product createProduct(Long brandId, String name) {
        Product product = Product.register(brandId, name, name + " 설명", 10000);
        Product saved = productRepository.save(product);
        inventoryRepository.save(Inventory.initialize(saved.getId(), 100));
        return saved;
    }

    private String likeUrl(Long productId) {
        return "/api/v1/products/" + productId + "/likes";
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class 상품_좋아요_등록 {

        @Test
        void 좋아요_등록에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 중복_좋아요는_409_Conflict를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");

            testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 존재하지_않는_상품이면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(999L), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 좋아요_등록_후_좋아요_레코드가_생성된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");

            // act
            testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert — 좋아요 레코드 생성 확인 (동기적으로 즉시 검증 가능)
            // likeCount 증분은 Outbox → Kafka → commerce-streamer Consumer 비동기 파이프라인이므로
            // 단일 앱(commerce-api) E2E에서는 검증 불가 — ProductLike 존재 여부로 대체
            assertThat(productLikeRepository.existsByUserIdAndProductId(1L, product.getId())).isTrue();
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class 상품_좋아요_취소 {

        @Test
        void 좋아요_취소에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");
            testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.DELETE, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 좋아요가_없으면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.DELETE, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 좋아요_취소_후_좋아요_레코드가_삭제된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");
            testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // act
            testRestTemplate.exchange(
                    likeUrl(product.getId()), HttpMethod.DELETE, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert — 좋아요 레코드 삭제 확인 (동기적으로 즉시 검증 가능)
            assertThat(productLikeRepository.existsByUserIdAndProductId(1L, product.getId())).isFalse();
        }
    }
}
