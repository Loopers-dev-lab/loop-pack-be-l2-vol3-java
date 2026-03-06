package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Like API E2E Tests")
class LikeApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";
    private static final String ENDPOINT_LIKES = "/likes";
    private static final String ENDPOINT_ME_LIKES = "/api/v1/me/likes";
    private static final String ENDPOINT_ADMIN_BRANDS = "/api-admin/v1/brands";
    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductJpaRepository productJpaRepository;
    private final LikeJpaRepository likeJpaRepository;

    private Long brandId;
    private Long categoryId;

    @Autowired
    public LikeApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            ProductJpaRepository productJpaRepository,
            LikeJpaRepository likeJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.productJpaRepository = productJpaRepository;
        this.likeJpaRepository = likeJpaRepository;
    }

    @BeforeEach
    void setUp() {
        brandId = createBrand("LIKE_TEST_BRAND");
        categoryId = createCategory("LIKE_TEST_CATEGORY");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/products/{productId}/likes")
    class Register {

        @Test
        @DisplayName("인증된 사용자가 활성 상품에 좋아요를 누르면 201을 반환한다")
        void registerLike_whenActiveProductAndAuthenticatedMember_returnsCreated() {
            registerMember("likeApiMember", "Password1!", "홍길동", "19900101", "api-like@example.com", "010-1234-5678");
            Long productId = createProduct("좋아요 상품", 10_000, 50);

            HttpHeaders headers = headers("likeApiMember", "Password1!");
            int beforeLikeCount = getProductLikeCount(productId);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(getProductLikeCount(productId)).isEqualTo(beforeLikeCount + 1);
        }

        @Test
        @DisplayName("이미 좋아요한 상품을 다시 누르면 409을 반환한다")
        void registerLike_whenAlreadyLikedProduct_returnsConflict() {
            registerMember("likeApiConfMem", "Password1!", "홍길동", "19900101", "api-like-conflict@example.com", "010-2345-6789");
            Long productId = createProduct("좋아요 상품", 10_000, 50);
            HttpHeaders headers = headers("likeApiConfMem", "Password1!");

            testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            ResponseEntity<ApiResponse<Void>> secondResponse = testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("삭제된 상품에 대해 좋아요 요청을 보내면 400을 반환한다")
        void registerLike_whenDeletedProduct_returnsBadRequest() {
            registerMember("likeApiDelMem", "Password1!", "홍길동", "19900101", "api-like-deleted@example.com", "010-3456-7890");
            Long productId = createProduct("삭제될 상품", 10_000, 50);
            deleteProductAsAdmin(productId);

            HttpHeaders headers = headers("likeApiDelMem", "Password1!");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요를 누르면 404를 반환한다")
        void registerLike_whenProductNotFound_returnsNotFound() {
            registerMember("likeApiMissMem", "Password1!", "홍길동", "19900101", "api-like-missing@example.com", "010-4567-8901");
            HttpHeaders headers = headers("likeApiMissMem", "Password1!");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(0L),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void registerLike_whenNoAuthentication_returnsUnauthorized() {
            Long productId = createProduct("좋아요 상품", 10_000, 50);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    class Cancel {

        @Test
        @DisplayName("좋아요 상태에서 삭제 요청하면 200을 반환한다")
        void cancelLike_whenLikedProduct_returnsOk() {
            registerMember("likeApiCancelMem", "Password1!", "홍길동", "19900101", "api-like-cancel@example.com", "010-6789-0123");
            Long productId = createProduct("좋아요 상품", 10_000, 50);
            HttpHeaders headers = headers("likeApiCancelMem", "Password1!");
            int beforeLikeCount = getProductLikeCount(productId);

            testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getProductLikeCount(productId)).isEqualTo(beforeLikeCount);
        }

        @Test
        @DisplayName("좋아요가 없는 상품 취소 요청은 404을 반환한다")
        void cancelLike_whenNotLikedProduct_returnsNotFound() {
            registerMember("likeApiCancelMissMem", "Password1!", "홍길동", "19900101", "api-like-cancel-missing@example.com", "010-7890-1233");
            Long productId = createProduct("좋아요 상품", 10_000, 50);
            HttpHeaders headers = headers("likeApiCancelMissMem", "Password1!");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void cancelLike_whenNoAuthentication_returnsUnauthorized() {
            Long productId = createProduct("좋아요 상품", 10_000, 50);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.DELETE,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/me/likes")
    class MyLikes {

        @Test
        @DisplayName("인증된 사용자가 기본 페이지/사이즈로 조회하면 200을 반환한다")
        void getMyLikes_whenAuthenticatedMemberAndDefaultPagination_returnsOk() {
            registerMember("likeApiMeLikes", "Password1!", "홍길동", "19900101", "api-like-mylikes@example.com", "010-9012-3456");
            Long productId = createProduct("좋아요 상품", 10_000, 50);
            HttpHeaders headers = headers("likeApiMeLikes", "Password1!");

            testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ME_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data()).isInstanceOf(java.util.Map.class);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getBody().data();
            assertThat(data.get("page")).isEqualTo(0);
            assertThat(data.get("size")).isEqualTo(20);
            assertThat(data.get("totalElements")).isEqualTo(1);

            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> items = (java.util.List<java.util.Map<String, Object>>) data.get("items");
            assertThat(items).hasSize(1);
            assertThat(((Number) items.get(0).get("id")).longValue()).isEqualTo(productId);
        }

        @Test
        @DisplayName("좋아요한 상품이 삭제되면 내 좋아요 목록에서 제외된다")
        void getMyLikes_whenLikedProductDeleted_excludesDeletedProduct() {
            registerMember("likeApiMeDeleted", "Password1!", "홍길동", "19900101", "api-like-mylikes-deleted@example.com", "010-9022-3456");
            Long productId = createProduct("삭제될 상품", 10_000, 50);
            HttpHeaders headers = headers("likeApiMeDeleted", "Password1!");

            testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            deleteProductAsAdmin(productId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ME_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data()).isInstanceOf(java.util.Map.class);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getBody().data();
            assertThat(data.get("totalElements")).isEqualTo(0);

            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> items = (java.util.List<java.util.Map<String, Object>>) data.get("items");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("브랜드 삭제 시 관련 상품 좋아요가 삭제되고 내 좋아요 목록에서 제외된다")
        void getMyLikes_whenBrandDeleted_removesRelatedLikesAndExcludesProducts() {
            registerMember("likeApiBrandDeleted", "Password1!", "홍길동", "19900101", "api-like-brand-deleted@example.com", "010-9032-3456");
            Long productId = createProduct("브랜드 삭제 대상 상품", 10_000, 50);
            HttpHeaders headers = headers("likeApiBrandDeleted", "Password1!");

            testRestTemplate.exchange(
                    productLikesUrl(productId),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {
                    }
            );

            assertThat(likeJpaRepository.existsByMemberIdAndProductId("likeApiBrandDeleted", productId)).isTrue();

            deleteBrandAsAdmin(brandId);

            assertThat(likeJpaRepository.existsByMemberIdAndProductId("likeApiBrandDeleted", productId)).isFalse();

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ME_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data()).isInstanceOf(java.util.Map.class);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getBody().data();
            assertThat(data.get("totalElements")).isEqualTo(0);

            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> items = (java.util.List<java.util.Map<String, Object>>) data.get("items");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void getMyLikes_whenNoAuthentication_returnsUnauthorized() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ME_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private HttpHeaders headers(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    private String productLikesUrl(long productId) {
        return ENDPOINT_PRODUCTS + "/" + productId + ENDPOINT_LIKES;
    }

    private int getProductLikeCount(long productId) {
        ResponseEntity<ApiResponse<JsonNode>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + productId,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data().path("likeCount").asInt();
    }

    private void deleteProductAsAdmin(long productId) {
        ProductEntity product = productJpaRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("product not found: " + productId));
        product.delete();
        productJpaRepository.save(product);
    }

    private void deleteBrandAsAdmin(long targetBrandId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP_VALUE);

        ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN_BRANDS + "/" + targetBrandId,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void registerMember(String loginId, String password, String name, String birthDate, String email, String phone) {
        MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                loginId,
                password,
                name,
                birthDate,
                email,
                phone
        );
        ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/members",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                }
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Long createProduct(String name, int price, int stock) {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name,
                price,
                stock,
                "desc",
                categoryId,
                brandId
        );

        ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNotNull();
        return response.getBody().data().id();
    }

    private Long createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }

    private Long createBrand(String name) {
        return brandRepository.save(new Brand(new BrandName(name), "", "")).id();
    }
}
