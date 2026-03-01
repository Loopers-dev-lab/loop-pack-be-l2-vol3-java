package com.loopers.interfaces.api;

import com.loopers.application.user.UserApplicationService;
import com.loopers.application.user.UserDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.Gender;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LikeE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        UserDto.UserInfo user = userApplicationService.register(
            "likeuser1",
            "TestPass1!",
            "좋아요유저",
            LocalDate.of(2000, 1, 1),
            "like-user@loopers.com",
            Gender.MALE
        );
        userId = user.id();

        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("LIKE_BRAND"), new BrandDescription("좋아요 브랜드"))
        );

        Product product = productJpaRepository.save(
            Product.create(brand.getId(), "좋아요 상품", "설명", BigDecimal.valueOf(30000), 5)
        );
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST → GET → DELETE /likes: 좋아요 등록/중복/목록조회/취소 흐름에 성공한다")
    @Test
    void like_flow_success() throws Exception {
        mockMvc.perform(post("/api/v1/products/" + productId + "/likes")
                .header("X-Loopers-User-Id", userId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.productId").value(productId))
            .andExpect(jsonPath("$.data.liked").value(true))
            .andExpect(jsonPath("$.data.message").value("success"));

        mockMvc.perform(post("/api/v1/products/" + productId + "/likes")
                .header("X-Loopers-User-Id", userId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.message").value("already_liked"));

        mockMvc.perform(get("/api/v1/users/" + userId + "/likes")
                .header("X-Loopers-User-Id", userId)
                .param("page", "0")
                .param("size", "20"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].productId").value(productId));

        mockMvc.perform(delete("/api/v1/products/" + productId + "/likes")
                .header("X-Loopers-User-Id", userId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/users/" + userId + "/likes")
                .header("X-Loopers-User-Id", userId)
                .param("page", "0")
                .param("size", "20"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @DisplayName("GET /users/{userId}/likes: 다른 유저의 좋아요 목록은 조회할 수 없다")
    @Test
    void like_list_forbidden_for_other_user() throws Exception {
        UserDto.UserInfo another = userApplicationService.register(
            "likeuser2",
            "TestPass2!",
            "다른유저",
            LocalDate.of(2001, 1, 1),
            "like-user2@loopers.com",
            Gender.FEMALE
        );

        mockMvc.perform(get("/api/v1/users/" + userId + "/likes")
                .header("X-Loopers-User-Id", another.id())
                .param("page", "0")
                .param("size", "20"))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }
}
