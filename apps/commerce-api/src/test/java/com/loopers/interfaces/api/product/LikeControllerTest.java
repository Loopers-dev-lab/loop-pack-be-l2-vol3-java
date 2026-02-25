package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.user.UserDto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Like API Controller Tests")
class LikeControllerTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final long ACTIVE_PRODUCT_ID = 1L;
    private static final long DELETED_PRODUCT_ID = 2L;
    private static final long NOT_FOUND_PRODUCT_ID = 9_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/products/{productId}/likes")
    class RegisterLike {

        @Test
        @DisplayName("인증된 사용자가 활성 상품을 좋아요하면 201을 반환한다")
        void registerLike_whenActiveProductAndAuthenticatedUser_returnsCreated() throws Exception {
            registerUser("likeUser", "Password1!", "홍길동", "19900101", "like@example.com", "010-1234-5678");
            mockMvc.perform(post("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "likeUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("이미 좋아요한 상품을 다시 요청하면 409을 반환한다")
        void registerLike_whenAlreadyLikedProduct_returnsConflict() throws Exception {
            registerUser("likeConflictUser", "Password1!", "홍길동", "19900101", "like2@example.com", "010-2345-6789");
            mockMvc.perform(post("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "likeConflictUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));

            mockMvc.perform(post("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "likeConflictUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("존재하지 않는 상품 ID면 404을 반환한다")
        void registerLike_whenProductNotFound_returnsNotFound() throws Exception {
            registerUser("likeMissingProductUser", "Password1!", "홍길동", "19900101", "missing@example.com", "010-3456-7890");

            mockMvc.perform(post("/api/v1/products/{productId}/likes", NOT_FOUND_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "likeMissingProductUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("삭제된 상품은 400 Bad Request를 반환한다")
        void registerLike_whenDeletedProduct_returnsBadRequest() throws Exception {
            registerUser("likeDeletedProductUser", "Password1!", "홍길동", "19900101", "deleted@example.com", "010-4567-8901");

            mockMvc.perform(post("/api/v1/products/{productId}/likes", DELETED_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "likeDeletedProductUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증이 없으면 401을 반환한다")
        void registerLike_whenNoAuthentication_returnsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    class CancelLike {

        @Test
        @DisplayName("좋아요 상태면 취소하고 200을 반환한다")
        void cancelLike_whenLikedProduct_returnsOk() throws Exception {
            registerUser("cancelLikeUser", "Password1!", "홍길동", "19900101", "cancel@example.com", "010-5678-9012");
            mockMvc.perform(post("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "cancelLikeUser")
                            .header(HEADER_LOGIN_PW, "Password1!"));

            mockMvc.perform(delete("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "cancelLikeUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("좋아요가 없으면 404을 반환한다")
        void cancelLike_whenNotLikedProduct_returnsNotFound() throws Exception {
            registerUser("cancelMissingLikeUser", "Password1!", "홍길동", "19900101", "cancel-missing@example.com", "010-6789-0123");

            mockMvc.perform(delete("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID)
                            .header(HEADER_LOGIN_ID, "cancelMissingLikeUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("인증이 없으면 401을 반환한다")
        void cancelLike_whenNoAuthentication_returnsUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/products/{productId}/likes", ACTIVE_PRODUCT_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/me/likes")
    class GetMyLikes {

        @Test
        @DisplayName("인증된 사용자가 기본 페이지/사이즈로 조회하면 200을 반환한다")
        void getMyLikes_whenAuthenticatedUserAndDefaultPagination_returnsOk() throws Exception {
            registerUser("myLikesUser", "Password1!", "홍길동", "19900101", "mylikes@example.com", "010-7890-1234");

            mockMvc.perform(get("/api/v1/me/likes")
                            .param("page", "0")
                            .param("size", "20")
                            .header(HEADER_LOGIN_ID, "myLikesUser")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("인증이 없으면 401을 반환한다")
        void getMyLikes_whenNoAuthentication_returnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/me/likes"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void registerUser(String loginId, String password, String name, String birthDate, String email, String phone)
            throws Exception {
        UserDto.RegisterRequest request = new UserDto.RegisterRequest(
                loginId,
                password,
                name,
                birthDate,
                email,
                phone
        );
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
