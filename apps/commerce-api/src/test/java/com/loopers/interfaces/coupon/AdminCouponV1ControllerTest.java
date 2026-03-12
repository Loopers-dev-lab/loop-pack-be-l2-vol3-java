package com.loopers.interfaces.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.dto.CouponCriteria;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.coupon.dto.AdminCouponV1Dto;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("AdminCouponV1Controller 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AdminCouponV1ControllerTest {

    @Mock
    private CouponFacade couponFacade;

    @InjectMocks
    private AdminCouponV1Controller adminCouponV1Controller;

    private final ZonedDateTime expiredAt = ZonedDateTime.now().plusMonths(3);
    private final ZonedDateTime now = ZonedDateTime.now();

    private CouponResult.Detail sampleDetail() {
        return new CouponResult.Detail(
                1L, "신규가입 10% 할인", "RATE", 10, 10000L,
                1000, 0, expiredAt, now, now);
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class RegisterCoupon {

        @DisplayName("쿠폰 등록 요청이면, 등록된 쿠폰 정보를 반환한다")
        @Test
        void register_returnsDetailResponse() {
            // arrange
            AdminCouponV1Dto.RegisterRequest request = new AdminCouponV1Dto.RegisterRequest(
                    "신규가입 10% 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, expiredAt);
            when(couponFacade.registerCoupon(any(CouponCriteria.Create.class)))
                    .thenReturn(sampleDetail());

            // act
            ApiResponse<AdminCouponV1Dto.DetailResponse> response =
                    adminCouponV1Controller.register(request);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().id()).isEqualTo(1L),
                () -> assertThat(response.data().name()).isEqualTo("신규가입 10% 할인")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class ListCoupons {

        @DisplayName("쿠폰 목록을 페이지네이션으로 반환한다")
        @Test
        void list_returnsPageResponse() {
            // arrange
            Page<CouponResult.Detail> page = new PageImpl<>(
                    List.of(sampleDetail()), PageRequest.of(0, 20), 1);
            when(couponFacade.getCoupons(any())).thenReturn(page);

            // act
            ApiResponse<AdminCouponV1Dto.ListResponse> response =
                    adminCouponV1Controller.list(0, 20);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().totalElements()).isEqualTo(1),
                () -> assertThat(response.data().items()).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetCoupon {

        @DisplayName("쿠폰 상세를 반환한다")
        @Test
        void getById_returnsDetailResponse() {
            // arrange
            when(couponFacade.getCoupon(1L)).thenReturn(sampleDetail());

            // act
            ApiResponse<AdminCouponV1Dto.DetailResponse> response =
                    adminCouponV1Controller.getById(1L);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().id()).isEqualTo(1L),
                () -> assertThat(response.data().discountType()).isEqualTo("RATE")
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateCoupon {

        @DisplayName("쿠폰 수정 요청이면, SUCCESS 응답을 반환한다")
        @Test
        void update_returnsSuccess() {
            // arrange
            AdminCouponV1Dto.UpdateRequest request =
                    new AdminCouponV1Dto.UpdateRequest("수정된 쿠폰명", expiredAt);

            // act
            ApiResponse<Object> response = adminCouponV1Controller.update(1L, request);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> verify(couponFacade).updateCoupon(any(), any(CouponCriteria.Update.class))
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteCoupon {

        @DisplayName("쿠폰 삭제 요청이면, SUCCESS 응답을 반환한다")
        @Test
        void delete_returnsSuccess() {
            // act
            ApiResponse<Object> response = adminCouponV1Controller.delete(1L);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> verify(couponFacade).deleteCoupon(1L)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class IssuedCoupons {

        @DisplayName("쿠폰 발급 내역을 페이지네이션으로 반환한다")
        @Test
        void issues_returnsPageResponse() {
            // arrange
            Page<CouponResult.IssuedDetail> page = new PageImpl<>(
                    List.of(new CouponResult.IssuedDetail(1L, 100L, "AVAILABLE", null, now)),
                    PageRequest.of(0, 20), 1);
            when(couponFacade.getIssuedCoupons(any(), any())).thenReturn(page);

            // act
            ApiResponse<AdminCouponV1Dto.IssueListResponse> response =
                    adminCouponV1Controller.issues(1L, 0, 20);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().totalElements()).isEqualTo(1),
                () -> assertThat(response.data().items()).hasSize(1),
                () -> assertThat(response.data().items().get(0).userId()).isEqualTo(100L)
            );
        }
    }
}
