package com.loopers.interfaces.api.coupon;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Coupon API V1 E2E 테스트")
class CouponV1ApiE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CouponService couponService;

    private static final String LOGIN_ID = "couponuser01";
    private static final String LOGIN_PW = "Test1234!@#";
    private Long couponId;

    @BeforeEach
    void setUp() throws Exception {
        registerUser(LOGIN_ID, LOGIN_PW, "쿠폰테스트유저");
        CouponModel coupon = couponService.createCoupon("테스트쿠폰", DiscountType.FIXED,
                BigDecimal.valueOf(5000), null, LocalDateTime.now().plusDays(30));
        couponId = coupon.getCouponId();
    }

    @Nested
    @DisplayName("POST /api/v1/coupons/{couponId}/issue - 쿠폰 발급")
    class IssueCouponTests {

        @Test
        @DisplayName("정상 발급 시 200 반환")
        void issueCoupon_WithValidAuth_ShouldReturn200() throws Exception {
            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.userCouponId").isNumber())
                    .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
        }

        @Test
        @DisplayName("인증 헤더 없으면 400 반환")
        void issueCoupon_WithoutAuth_ShouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("없는 쿠폰 발급 시 404 반환")
        void issueCoupon_CouponNotFound_ShouldReturn404() throws Exception {
            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", 999L)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.errorCode").value("COUPON_NOT_FOUND"));
        }

        @Test
        @DisplayName("이미 발급된 쿠폰 재발급 시 409 반환")
        void issueCoupon_AlreadyIssued_ShouldReturn409() throws Exception {
            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                    .header("X-Loopers-LoginId", LOGIN_ID)
                    .header("X-Loopers-LoginPw", LOGIN_PW));

            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.meta.errorCode").value("COUPON_ALREADY_ISSUED"));
        }

        @Test
        @DisplayName("만료된 쿠폰 발급 시 400 반환")
        void issueCoupon_ExpiredCoupon_ShouldReturn400() throws Exception {
            CouponModel expiredCoupon = couponService.createCoupon("만료쿠폰", DiscountType.FIXED,
                    BigDecimal.valueOf(1000), null, LocalDateTime.now().minusDays(1));

            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", expiredCoupon.getCouponId())
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.meta.errorCode").value("COUPON_NOT_APPLICABLE"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/coupons - 내 쿠폰 목록")
    class GetMyCouponsTests {

        @Test
        @DisplayName("발급된 쿠폰이 있으면 목록을 반환한다")
        void getMyCoupons_ShouldReturnIssuedCoupons() throws Exception {
            mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                    .header("X-Loopers-LoginId", LOGIN_ID)
                    .header("X-Loopers-LoginPw", LOGIN_PW));

            mockMvc.perform(get("/api/v1/users/me/coupons")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].couponId").value(couponId));
        }

        @Test
        @DisplayName("발급된 쿠폰이 없으면 빈 배열을 반환한다")
        void getMyCoupons_NoIssuedCoupons_ShouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/users/me/coupons")
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
