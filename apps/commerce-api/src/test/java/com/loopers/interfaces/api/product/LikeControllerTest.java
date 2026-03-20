package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Like API Controller Tests")
class LikeControllerTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    private UUID brandId;
    private UUID categoryId;

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
    class RegisterLike {

        @Test
        @DisplayName("인증된 사용자가 활성 상품을 좋아요하면 201을 반환한다")
        void registerLike_whenActiveProductAndAuthenticatedMember_returnsCreated() throws Exception {
            registerMember("likeMember", "Password1!", "홍길동", "19900101", "like@example.com", "010-1234-5678");
            UUID productId = createProduct("좋아요 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "likeMember")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("이미 좋아요한 상품을 다시 요청하면 409을 반환한다")
        void registerLike_whenAlreadyLikedProduct_returnsConflict() throws Exception {
            registerMember("likeConflictMember", "Password1!", "홍길동", "19900101", "like2@example.com", "010-2345-6789");
            UUID productId = createProduct("좋아요 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "likeConflictMember")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "likeConflictMember")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("존재하지 않는 상품 ID면 404을 반환한다")
        void registerLike_whenProductNotFound_returnsNotFound() throws Exception {
            registerMember("likeMissingMem", "Password1!", "홍길동", "19900101", "missing@example.com", "010-3456-7890");

            mockMvc.perform(post("/api/v1/products/{productId}/likes", UUID.randomUUID())
                            .header(HEADER_LOGIN_ID, "likeMissingMem")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("삭제된 상품은 400 Bad Request를 반환한다")
        void registerLike_whenDeletedProduct_returnsBadRequest() throws Exception {
            registerMember("likeDeletedMem", "Password1!", "홍길동", "19900101", "deleted@example.com", "010-4567-8901");
            UUID productId = createProduct("삭제될 상품", 10_000, 50, "테스트 상품", categoryId, brandId);
            deleteProduct(productId);

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "likeDeletedMem")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증이 없으면 401을 반환한다")
        void registerLike_whenNoAuthentication_returnsUnauthorized() throws Exception {
            UUID productId = createProduct("좋아요 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    class CancelLike {

        @Test
        @DisplayName("좋아요 상태면 취소하고 200을 반환한다")
        void cancelLike_whenLikedProduct_returnsOk() throws Exception {
            registerMember("cancelLikeMember", "Password1!", "홍길동", "19900101", "cancel@example.com", "010-5678-9012");
            UUID productId = createProduct("좋아요 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "cancelLikeMember")
                            .header(HEADER_LOGIN_PW, "Password1!"));

            mockMvc.perform(delete("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "cancelLikeMember")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("좋아요가 없으면 404을 반환한다")
        void cancelLike_whenNotLikedProduct_returnsNotFound() throws Exception {
            registerMember("cancelMissingMem", "Password1!", "홍길동", "19900101", "cancel-missing@example.com", "010-6789-0123");
            UUID productId = createProduct("좋아요 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(delete("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "cancelMissingMem")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("인증이 없으면 401을 반환한다")
        void cancelLike_whenNoAuthentication_returnsUnauthorized() throws Exception {
            UUID productId = createProduct("좋아요 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(delete("/api/v1/products/{productId}/likes", productId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/me/likes")
    class GetMyLikes {

        @Test
        @DisplayName("인증된 사용자가 기본 페이지/사이즈로 조회하면 200을 반환한다")
        void getMyLikes_whenAuthenticatedMemberAndDefaultPagination_returnsOk() throws Exception {
            registerMember("myLikesMember", "Password1!", "홍길동", "19900101", "mylikes@example.com", "010-7890-1234");
            UUID productId = createProduct("좋아요 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "myLikesMember")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/me/likes")
                            .param("page", "0")
                            .param("size", "20")
                            .header(HEADER_LOGIN_ID, "myLikesMember")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(productId.toString()));
        }

        @Test
        @DisplayName("좋아요한 상품이 삭제되면 목록에서 제외된다")
        void getMyLikes_whenLikedProductDeleted_excludesDeletedProduct() throws Exception {
            registerMember("mylikesDeletedMem", "Password1!", "홍길동", "19900101", "mylikes-deleted@example.com", "010-7890-5555");
            UUID productId = createProduct("삭제될 상품", 10_000, 50, "테스트 상품", categoryId, brandId);

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header(HEADER_LOGIN_ID, "mylikesDeletedMem")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isCreated());

            deleteProduct(productId);

            mockMvc.perform(get("/api/v1/me/likes")
                            .param("page", "0")
                            .param("size", "20")
                            .header(HEADER_LOGIN_ID, "mylikesDeletedMem")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.items").isEmpty());
        }

        @Test
        @DisplayName("인증이 없으면 401을 반환한다")
        void getMyLikes_whenNoAuthentication_returnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/me/likes"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void registerMember(String loginId, String password, String name, String birthDate, String email, String phone)
            throws Exception {
        MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                loginId,
                password,
                name,
                birthDate,
                email,
                phone
        );
        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    private void deleteProduct(UUID productId) {
        ProductEntity product = productJpaRepository.findByReferenceId(productId)
                .orElseThrow(() -> new IllegalArgumentException("product not found: " + productId));
        product.delete();
        productJpaRepository.save(product);
    }

    private UUID createProduct(String name, int price, int stock, String description, UUID categoryId, UUID brandId) throws Exception {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name,
                price,
                stock,
                description,
                categoryId,
                brandId
        );

        String body = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }

    private UUID createBrand(String name) {
        return brandRepository.save(new Brand(new BrandName(name), "", "")).id();
    }
}
