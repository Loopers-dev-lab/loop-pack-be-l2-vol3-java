package com.loopers.interfaces.api.like;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.interfaces.api.user.UserV1Dto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Like API V1 E2E 테스트")
class LikeV1ApiE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BrandJpaRepository brandJpaRepository;

    @Autowired
    ProductJpaRepository productJpaRepository;

    @Autowired
    ProductStockJpaRepository productStockJpaRepository;

    private static final String LOGIN_ID = "likeuser01";
    private static final String LOGIN_PW = "Test1234!@#";
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        registerUser(LOGIN_ID, LOGIN_PW, "테스트유저");

        BrandModel brand = BrandModel.create("테스트브랜드", "설명", "서울");
        brand = brandJpaRepository.save(brand);

        ProductModel product = ProductModel.create("테스트상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "상품설명", null, null, null, null, null, null);
        product = productJpaRepository.save(product);
        productId = product.getProductId();

        ProductStockModel stock = ProductStockModel.create(productId, 100);
        productStockJpaRepository.save(stock);
    }

    @Nested
    @DisplayName("POST /api/v1/products/{productId}/likes - 좋아요 등록")
    class AddLikeTests {

        @Test
        @DisplayName("정상 등록 시 200 반환")
        void POST_addLike_ShouldReturn200() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("멱등 등록 시 2번 호출 모두 200 반환")
        void POST_addLike_Idempotent_ShouldReturn200() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("없는 상품에 좋아요 시 404 반환")
        void POST_addLike_ProductNotFound_ShouldReturn404() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", 999L)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.errorCode").value("LIKE_PRODUCT_NOT_FOUND"));
        }

        @Test
        @DisplayName("인증 헤더 없이 요청 시 400 반환")
        void POST_addLike_WithoutAuth_ShouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 인증 정보로 요청 시 401 반환")
        void POST_addLike_WrongAuth_ShouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", "WrongPass123!"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{productId}/likes - 좋아요 취소")
    class RemoveLikeTests {

        @Test
        @DisplayName("기존 좋아요 취소 시 200 반환")
        void DELETE_removeLike_ShouldReturn200() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                    .header("X-Loopers-LoginId", LOGIN_ID)
                    .header("X-Loopers-LoginPw", LOGIN_PW));

            mockMvc.perform(delete("/api/v1/products/{productId}/likes", productId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("없는 좋아요 취소 시에도 200 반환 (멱등)")
        void DELETE_removeLike_NotLiked_ShouldReturn200() throws Exception {
            mockMvc.perform(delete("/api/v1/products/{productId}/likes", productId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/likes - 내 좋아요 목록 조회")
    class GetMyLikesTests {

        @Test
        @DisplayName("좋아요 목록 조회 시 200 반환 및 데이터 검증")
        void GET_myLikes_ShouldReturn200() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                    .header("X-Loopers-LoginId", LOGIN_ID)
                    .header("X-Loopers-LoginPw", LOGIN_PW));

            mockMvc.perform(get("/api/v1/users/me/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].productId").value(productId));
        }

        @Test
        @DisplayName("좋아요가 없는 경우 빈 배열 반환")
        void GET_myLikes_Empty_ShouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/users/me/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("좋아요 등록 후 취소 시 빈 목록 반환")
        void GET_myLikes_AfterAddAndRemove_ShouldReturnEmptyList() throws Exception {
            mockMvc.perform(post("/api/v1/products/{productId}/likes", productId)
                    .header("X-Loopers-LoginId", LOGIN_ID)
                    .header("X-Loopers-LoginPw", LOGIN_PW));

            mockMvc.perform(delete("/api/v1/products/{productId}/likes", productId)
                    .header("X-Loopers-LoginId", LOGIN_ID)
                    .header("X-Loopers-LoginPw", LOGIN_PW));

            mockMvc.perform(get("/api/v1/users/me/likes")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    private void registerUser(String loginId, String password, String userName) throws Exception {
        var request = UserV1Dto.RegisterRequest.builder()
                .loginId(loginId)
                .password(password)
                .userName(userName)
                .birthday("19900101")
                .email("test@example.com")
                .address("서울")
                .build();

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
