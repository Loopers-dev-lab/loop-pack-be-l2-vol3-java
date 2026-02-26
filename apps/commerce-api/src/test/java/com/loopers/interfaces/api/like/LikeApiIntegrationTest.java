package com.loopers.interfaces.api.like;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateRequest;
import com.loopers.interfaces.api.user.dto.UserRegisterRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
class LikeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String LIKE_URL = "/api/v1/products";
    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";
    private static final String LOGIN_ID = "testuser1";
    private static final String PASSWORD = "Password1!";
    private static final String MY_LIKES_URL = "/api/v1/users/" + LOGIN_ID + "/likes";

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanUp.truncateAllTables();
        registerUser(LOGIN_ID, PASSWORD, "홍길동");
        createBrand("나이키", "스포츠");
        createProduct(1L, "운동화", 50000, 100);
    }

    @Nested
    @DisplayName("좋아요 API")
    class LikeApi {

        @Test
        @DisplayName("좋아요 성공")
        void like_success() throws Exception {
            mockMvc.perform(post(LIKE_URL + "/1/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("좋아요 후 상품 likeCount 증가 확인")
        void like_then_checkLikeCount() throws Exception {
            mockMvc.perform(post(LIKE_URL + "/1/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").value(1));
        }

        @Test
        @DisplayName("인증 없이 좋아요 시 실패")
        void like_fail_unauthorized() throws Exception {
            mockMvc.perform(post(LIKE_URL + "/1/likes"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("좋아요 취소 API")
    class UnlikeApi {

        @Test
        @DisplayName("좋아요 취소 성공")
        void unlike_success() throws Exception {
            // 먼저 좋아요
            mockMvc.perform(post(LIKE_URL + "/1/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk());

            // 좋아요 취소
            mockMvc.perform(delete(LIKE_URL + "/1/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk());

            // likeCount 0 확인
            mockMvc.perform(get("/api/v1/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").value(0));
        }
    }

    @Nested
    @DisplayName("좋아요 목록 조회 API")
    class GetMyLikesApi {

        @Test
        @DisplayName("좋아요 목록 조회 성공")
        void getMyLikes_success() throws Exception {
            // 좋아요
            mockMvc.perform(post(LIKE_URL + "/1/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk());

            // 목록 조회
            mockMvc.perform(get(MY_LIKES_URL)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].productId").value(1))
                    .andExpect(jsonPath("$[0].productName").value("운동화"));
        }

        @Test
        @DisplayName("좋아요 없는 경우 빈 목록")
        void getMyLikes_empty() throws Exception {
            mockMvc.perform(get(MY_LIKES_URL)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    private void registerUser(String loginId, String password, String name) throws Exception {
        var request = new UserRegisterRequest(loginId, password, name,
                LocalDate.of(1990, 5, 15), "test@example.com");
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void createBrand(String name, String description) throws Exception {
        var request = new BrandCreateRequest(name, description);
        mockMvc.perform(post("/api-admin/v1/brands")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void createProduct(Long brandId, String name, int price, int stock) throws Exception {
        var request = new ProductCreateRequest(brandId, name, price, null, stock, "설명");
        mockMvc.perform(post("/api-admin/v1/products")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
