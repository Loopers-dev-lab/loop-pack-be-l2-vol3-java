package com.loopers.interfaces.apiadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.support.enums.DiscountType;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 Coupon API V1 E2E 테스트")
class AdminCouponV1ApiE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CouponService couponService;

    private static final String ADMIN_LDAP = "loopers.admin";
    private static final String USER_LOGIN_ID = "admincoupontest";
    private static final String USER_LOGIN_PW = "Test1234!@#";
    private Long couponId;

    @BeforeEach
    void setUp() throws Exception {
        CouponModel coupon = couponService.createCoupon("기본쿠폰", DiscountType.FIXED,
                BigDecimal.valueOf(1000), null, LocalDateTime.now().plusDays(30), null);
        couponId = coupon.getCouponId();
    }

    @Nested
    @DisplayName("POST /api-admin/v1/coupons - 쿠폰 등록")
    class CreateCouponTests {

        @Test
        @DisplayName("정상 등록 시 200 반환")
        void createCoupon_ShouldReturn200() throws Exception {
            Map<String, Object> request = Map.of(
                    "name", "신규쿠폰",
                    "type", "RATE",
                    "value", 10,
                    "expiredAt", "2030-12-31T23:59:59"
            );

            mockMvc.perform(post("/api-admin/v1/coupons")
                            .header("X-Loopers-Ldap", ADMIN_LDAP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.couponId").isNumber())
                    .andExpect(jsonPath("$.data.name").value("신규쿠폰"));
        }

        @Test
        @DisplayName("LDAP 헤더 없으면 401 반환")
        void createCoupon_WithoutLdap_ShouldReturn401() throws Exception {
            Map<String, Object> request = Map.of(
                    "name", "쿠폰",
                    "type", "FIXED",
                    "value", 1000,
                    "expiredAt", "2030-12-31T23:59:59"
            );

            mockMvc.perform(post("/api-admin/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/coupons/{couponId} - 쿠폰 상세")
    class GetCouponTests {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getCoupon_ShouldReturn200() throws Exception {
            mockMvc.perform(get("/api-admin/v1/coupons/{couponId}", couponId)
                            .header("X-Loopers-Ldap", ADMIN_LDAP))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.couponId").value(couponId));
        }

        @Test
        @DisplayName("없는 쿠폰 조회 시 404 반환")
        void getCoupon_NotFound_ShouldReturn404() throws Exception {
            mockMvc.perform(get("/api-admin/v1/coupons/{couponId}", 99999L)
                            .header("X-Loopers-Ldap", ADMIN_LDAP))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.errorCode").value("COUPON_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PUT /api-admin/v1/coupons/{couponId} - 쿠폰 수정")
    class UpdateCouponTests {

        @Test
        @DisplayName("정상 수정 시 200 반환")
        void updateCoupon_ShouldReturn200() throws Exception {
            Map<String, Object> request = Map.of(
                    "name", "수정된쿠폰",
                    "type", "RATE",
                    "value", 20,
                    "expiredAt", "2031-12-31T23:59:59"
            );

            mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", couponId)
                            .header("X-Loopers-Ldap", ADMIN_LDAP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("수정된쿠폰"));
        }
    }

    @Nested
    @DisplayName("DELETE /api-admin/v1/coupons/{couponId} - 쿠폰 삭제")
    class DeleteCouponTests {

        @Test
        @DisplayName("정상 삭제 시 200 반환")
        void deleteCoupon_ShouldReturn200() throws Exception {
            mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", couponId)
                            .header("X-Loopers-Ldap", ADMIN_LDAP))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("없는 쿠폰 삭제 시 404 반환")
        void deleteCoupon_NotFound_ShouldReturn404() throws Exception {
            mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", 99999L)
                            .header("X-Loopers-Ldap", ADMIN_LDAP))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.errorCode").value("COUPON_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/coupons - 쿠폰 목록 (페이징)")
    class GetCouponsPagedTests {

        @Test
        @DisplayName("페이징 응답 구조로 반환된다")
        void getCoupons_ShouldReturnPagedResponse() throws Exception {
            mockMvc.perform(get("/api-admin/v1/coupons")
                            .header("X-Loopers-Ldap", ADMIN_LDAP)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.totalElements").isNumber())
                    .andExpect(jsonPath("$.data.totalPages").isNumber());
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues - 발급 내역 (페이징)")
    class GetIssueHistoryTests {

        @Test
        @DisplayName("발급 내역을 페이징 응답 구조로 반환한다")
        void getIssueHistory_ShouldReturnPagedUserCoupons() throws Exception {
            // 사용자 등록 및 쿠폰 발급
            registerUser(USER_LOGIN_ID, USER_LOGIN_PW, "관리자테스트유저");
            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                    .header("X-Loopers-LoginId", USER_LOGIN_ID)
                    .header("X-Loopers-LoginPw", USER_LOGIN_PW));

            mockMvc.perform(get("/api-admin/v1/coupons/{couponId}/issues", couponId)
                            .header("X-Loopers-Ldap", ADMIN_LDAP)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20))
                    .andExpect(jsonPath("$.data.totalElements").isNumber())
                    .andExpect(jsonPath("$.data.totalPages").isNumber());
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
