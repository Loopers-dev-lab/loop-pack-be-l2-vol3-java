package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
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
class UserLikeApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
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

    private void likeProduct(Long productId) {
        testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), ApiResponse.class);
    }

    private void likeBrand(Long brandId) {
        testRestTemplate.exchange(
                "/api/v1/brands/" + brandId + "/likes", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), ApiResponse.class);
    }

    private Long getAuthUserId() {
        return userRepository.findByLoginId("testuser").orElseThrow().getId();
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class 내_상품_좋아요_목록 {

        @Test
        void 내_좋아요_상품_목록을_조회하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스");
            likeProduct(product.getId());
            Long userId = getAuthUserId();

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/" + userId + "/likes", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 좋아요가_없으면_빈_목록과_200_OK를_반환한다() {
            // arrange
            Long userId = getAuthUserId();

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/" + userId + "/likes", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 삭제된_상품은_목록에서_제외된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product active = createProduct(brand.getId(), "활성상품");
            Product toDelete = createProduct(brand.getId(), "삭제예정");
            likeProduct(active.getId());
            likeProduct(toDelete.getId());

            // 상품 삭제
            Product loaded = productRepository.findById(toDelete.getId()).orElseThrow();
            loaded.discontinue();
            productRepository.save(loaded);

            Long userId = getAuthUserId();

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/" + userId + "/likes", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 타인의_좋아요_목록을_조회하면_403_Forbidden을_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/99999/likes", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api/v1/users/me/brand-likes")
    @Nested
    class 내_브랜드_좋아요_목록 {

        @Test
        void 내_좋아요_브랜드_목록을_조회하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            likeBrand(brand.getId());

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/brand-likes", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 좋아요가_없으면_빈_목록과_200_OK를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/brand-likes", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
