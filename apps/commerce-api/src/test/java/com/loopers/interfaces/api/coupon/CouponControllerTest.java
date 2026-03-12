package com.loopers.interfaces.api.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Coupon API Controller 통합 테스트")
class CouponControllerTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String TEST_LOGIN_ID = "coupontestuser";
    private static final String TEST_PASSWORD = "Test1234!@";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, Object> registerRequest = Map.of(
                "loginId", TEST_LOGIN_ID,
                "password", TEST_PASSWORD,
                "name", "쿠폰이",
                "birthDate", "19900101",
                "email", "coupon@test.com",
                "phone", "010-1234-5678"
        );

        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    class IssueCoupon {
        @Test
        @DisplayName("인증 없이 발급 요청하면 401")
        void issueWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/v1/coupons/00000000-0000-0000-0000-000000000001/issue"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 couponId로 발급 요청하면 404")
        void issueInvalidCouponId() throws Exception {
            mockMvc.perform(post("/api/v1/coupons/00000000-0000-0000-0000-000000000999/issue")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/coupons")
    class ListMyCoupons {
        @Test
        @DisplayName("인증 없이 조회하면 401")
        void listWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/users/me/coupons"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("인증 시 조회 성공(200)")
        void listWithAuth() throws Exception {
            mockMvc.perform(get("/api/v1/users/me/coupons")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isOk());
        }
    }
}
