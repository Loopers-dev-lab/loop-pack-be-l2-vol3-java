package com.loopers.interfaces.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.coupon.dto.CouponV1Dto;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("CouponV1Controller 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CouponV1ControllerTest {

    @Mock
    private CouponFacade couponFacade;

    @InjectMocks
    private CouponV1Controller couponV1Controller;

    private final LoginUser loginUser = new LoginUser(1L, "testuser", "테스터");

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("쿠폰 발급 요청이면, SUCCESS 응답을 반환한다")
        @Test
        void issue_returnsSuccess() {
            // arrange
            when(couponFacade.issueCoupon(10L, 1L)).thenReturn(
                    new CouponResult.IssuedDetail(1L, 1L, "AVAILABLE", null, ZonedDateTime.now()));

            // act
            ApiResponse<Object> response = couponV1Controller.issue(loginUser, 10L);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> verify(couponFacade).issueCoupon(10L, 1L)
            );
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class MyOwnedCoupons {

        @DisplayName("내 쿠폰 목록을 SUCCESS 응답으로 반환한다")
        @Test
        void myOwnedCoupons_returnsList() {
            // arrange
            when(couponFacade.getMyOwnedCoupons(1L)).thenReturn(List.of(
                    new CouponResult.OwnedDetail(
                            1L, 10L, "신규가입 10% 할인", "RATE", 10,
                            10000L, "AVAILABLE",
                            ZonedDateTime.now().plusMonths(3), null,
                            ZonedDateTime.now())));

            // act
            ApiResponse<CouponV1Dto.OwnedCouponListResponse> response =
                    couponV1Controller.myOwnedCoupons(loginUser);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().items()).hasSize(1),
                () -> assertThat(response.data().items().get(0).couponName()).isEqualTo("신규가입 10% 할인")
            );
        }
    }
}
