package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private BrandEntity savedBrand;
    private UserModel savedUser;
    private static final String LOGIN_ID = "testuser";
    private static final String LOGIN_PW = "Password1!";

    @BeforeEach
    void setUp() {
        savedBrand = brandJpaRepository.save(createBrandEntity("나이키", "스포츠 브랜드"));
        savedUser = userJpaRepository.save(
            UserModel.create(LOGIN_ID, passwordEncoder.encode(LOGIN_PW), LocalDate.of(1990, 1, 1), "테스트유저", "test@test.com")
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/products/{productId}/like")
    class ToggleLike {

        @Test
        @DisplayName("좋아요를 누르면, 좋아요가 추가된다")
        void success_add_like() {
            ProductEntity product = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 0)
            );

            String url = "/api/v1/products/" + product.getId() + "/like";
            HttpHeaders headers = createAuthHeaders();
            var responseType = new ParameterizedTypeReference<ApiResponse<Object>>() {};

            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(null, headers), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(likeJpaRepository.existsByRefProductIdAndRefUserId(product.getId(), savedUser.getId())).isTrue()
            );
        }

        @Test
        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 누르면, 좋아요가 취소된다")
        void success_remove_like() {
            ProductEntity product = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 1)
            );

            String url = "/api/v1/products/" + product.getId() + "/like";
            HttpHeaders headers = createAuthHeaders();
            var responseType = new ParameterizedTypeReference<ApiResponse<Object>>() {};

            // 첫 번째 좋아요
            testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(null, headers), responseType);
            assertThat(likeJpaRepository.existsByRefProductIdAndRefUserId(product.getId(), savedUser.getId())).isTrue();

            // 두 번째 좋아요 (취소)
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(null, headers), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(likeJpaRepository.existsByRefProductIdAndRefUserId(product.getId(), savedUser.getId())).isFalse()
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요를 누르면, 400 응답을 반환한다")
        void fail_when_product_not_found() {
            String url = "/api/v1/products/99999/like";
            HttpHeaders headers = createAuthHeaders();
            var responseType = new ParameterizedTypeReference<ApiResponse<Object>>() {};

            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(null, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("인증 헤더가 없으면, 401 응답을 반환한다")
        void fail_when_unauthorized() {
            ProductEntity product = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 0)
            );

            String url = "/api/v1/products/" + product.getId() + "/like";
            var responseType = new ParameterizedTypeReference<ApiResponse<Object>>() {};

            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(null), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LoopersHeaders.X_LOOPERS_LOGIN_ID, LOGIN_ID);
        headers.set(LoopersHeaders.X_LOOPERS_LOGIN_PW, LOGIN_PW);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private BrandEntity createBrandEntity(String name, String description) {
        try {
            BrandEntity entity = BrandEntity.class.getDeclaredConstructor().newInstance();

            var nameField = BrandEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var descField = BrandEntity.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(entity, description);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProductEntity createProductEntity(String name, Long brandId, int price, int stock, int likeCount) {
        try {
            ProductEntity entity = ProductEntity.class.getDeclaredConstructor().newInstance();

            var nameField = ProductEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var brandIdField = ProductEntity.class.getDeclaredField("refBrandId");
            brandIdField.setAccessible(true);
            brandIdField.set(entity, brandId);

            var priceField = ProductEntity.class.getDeclaredField("price");
            priceField.setAccessible(true);
            priceField.set(entity, price);

            var stockField = ProductEntity.class.getDeclaredField("stock");
            stockField.setAccessible(true);
            stockField.set(entity, stock);

            var likeCountField = ProductEntity.class.getDeclaredField("likeCount");
            likeCountField.setAccessible(true);
            likeCountField.set(entity, likeCount);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
