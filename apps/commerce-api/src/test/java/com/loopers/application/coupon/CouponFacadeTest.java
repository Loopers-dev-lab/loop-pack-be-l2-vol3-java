package com.loopers.application.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.coupon.dto.CouponCriteria;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.CouponErrorCode;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.OwnedCouponModel;
import com.loopers.domain.coupon.CouponIssueLimiter;
import com.loopers.infrastructure.outbox.OutboxEventPublisher;
import com.loopers.support.error.CoreException;
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

@DisplayName("CouponFacade 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CouponFacadeTest {

    @Mock
    private CouponService couponService;

    @Mock
    private CouponIssueLimiter couponIssueLimiter;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private CouponFacade couponFacade;

    @DisplayName("쿠폰을 등록할 때 (UC-C01), ")
    @Nested
    class RegisterCoupon {

        @DisplayName("CouponService에 위임하고 등록된 쿠폰 상세를 반환한다")
        @Test
        void registerCoupon_success() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "신규가입 10% 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, ZonedDateTime.now().plusDays(30));
            when(couponService.register(any())).thenReturn(coupon);

            CouponCriteria.Create criteria = new CouponCriteria.Create(
                    "신규가입 10% 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, ZonedDateTime.now().plusDays(30));

            // act
            CouponResult.Detail result = couponFacade.registerCoupon(criteria);

            // assert
            assertAll(
                    () -> verify(couponService).register(any()),
                    () -> assertThat(result.name()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(result.discountType()).isEqualTo("RATE"),
                    () -> assertThat(result.discountValue()).isEqualTo(10L),
                    () -> assertThat(result.issuedQuantity()).isEqualTo(0L));
        }
    }

    @DisplayName("쿠폰을 상세 조회할 때 (UC-C02), ")
    @Nested
    class GetCoupon {

        @DisplayName("CouponService에 위임하고 쿠폰 상세를 반환한다")
        @Test
        void getCoupon_success() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "여름 세일 5000원 할인", CouponDiscountType.FIXED, 5000L,
                    20000L, 500, ZonedDateTime.now().plusDays(14));
            when(couponService.getById(1L)).thenReturn(coupon);
            when(couponService.countIssuedCoupons(1L)).thenReturn(42L);

            // act
            CouponResult.Detail result = couponFacade.getCoupon(1L);

            // assert
            assertAll(
                    () -> verify(couponService).getById(1L),
                    () -> assertThat(result.name()).isEqualTo("여름 세일 5000원 할인"),
                    () -> assertThat(result.discountType()).isEqualTo("FIXED"),
                    () -> assertThat(result.discountValue()).isEqualTo(5000L),
                    () -> assertThat(result.minOrderAmount()).isEqualTo(20000L),
                    () -> assertThat(result.issuedQuantity()).isEqualTo(42L));
        }
    }

    @DisplayName("쿠폰 목록을 조회할 때 (UC-C03), ")
    @Nested
    class GetCoupons {

        @DisplayName("CouponService에 위임하고 페이지네이션된 결과를 반환한다")
        @Test
        void getCoupons_success() {
            // arrange
            CouponModel coupon1 = CouponModel.create(
                    "신규가입 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, ZonedDateTime.now().plusDays(30));
            CouponModel coupon2 = CouponModel.create(
                    "여름 세일", CouponDiscountType.FIXED, 5000L,
                    20000L, 500, ZonedDateTime.now().plusDays(14));
            PageRequest pageable = PageRequest.of(0, 20);
            when(couponService.getAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(coupon1, coupon2), pageable, 2));
            when(couponService.countIssuedCoupons(any(List.class))).thenReturn(java.util.Map.of());

            // act
            Page<CouponResult.Detail> result = couponFacade.getCoupons(pageable);

            // assert
            assertAll(
                    () -> verify(couponService).getAll(pageable),
                    () -> assertThat(result.getContent()).hasSize(2),
                    () -> assertThat(result.getContent().get(0).name()).isEqualTo("신규가입 할인"),
                    () -> assertThat(result.getContent().get(1).name()).isEqualTo("여름 세일"));
        }
    }

    @DisplayName("쿠폰을 수정할 때 (UC-C02), ")
    @Nested
    class UpdateCoupon {

        @DisplayName("CouponService에 위임한다")
        @Test
        void updateCoupon_success() {
            // arrange
            CouponCriteria.Update criteria = new CouponCriteria.Update(
                    "수정된 쿠폰명", ZonedDateTime.now().plusDays(60));

            // act
            couponFacade.updateCoupon(1L, criteria);

            // assert
            verify(couponService).update(eq(1L), any());
        }
    }

    @DisplayName("쿠폰을 삭제할 때 (UC-C03), ")
    @Nested
    class DeleteCoupon {

        @DisplayName("CouponService에 위임한다")
        @Test
        void deleteCoupon_success() {
            // act
            couponFacade.deleteCoupon(1L);

            // assert
            verify(couponService).delete(1L);
        }
    }

    @DisplayName("쿠폰 발급 내역을 조회할 때 (UC-C06), ")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("CouponService에 위임하고 페이지네이션된 발급 내역을 반환한다")
        @Test
        void getIssuedCoupons_success() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "신규가입 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, ZonedDateTime.now().plusDays(30));
            OwnedCouponModel owned1 = OwnedCouponModel.create(coupon, 100L);
            OwnedCouponModel owned2 = OwnedCouponModel.create(coupon, 200L);
            PageRequest pageable = PageRequest.of(0, 20);
            when(couponService.getIssuedCoupons(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(owned1, owned2), pageable, 2));

            // act
            Page<CouponResult.IssuedDetail> result =
                    couponFacade.getIssuedCoupons(1L, pageable);

            // assert
            assertAll(
                    () -> verify(couponService).getIssuedCoupons(1L, pageable),
                    () -> assertThat(result.getContent()).hasSize(2),
                    () -> assertThat(result.getContent().get(0).userId()).isEqualTo(100L),
                    () -> assertThat(result.getContent().get(0).status()).isEqualTo("AVAILABLE"));
        }
    }

    @DisplayName("쿠폰을 발급할 때 (UC-C07), ")
    @Nested
    class IssueCoupon {

        @DisplayName("Redis 승인 후 Outbox에 저장하고 PENDING 상태를 반환한다")
        @Test
        void issueCoupon_success() {
            // arrange
            when(couponIssueLimiter.tryIssue(1L, 100L))
                    .thenReturn(CouponIssueResult.SUCCESS);

            // act
            CouponResult.IssuedDetail result = couponFacade.issueCoupon(1L, 100L);

            // assert
            assertAll(
                    () -> verify(couponIssueLimiter).tryIssue(1L, 100L),
                    () -> verify(outboxEventPublisher).publish(
                            eq("COUPON_ISSUED"), eq("coupon-issued"), eq("1"), any()),
                    () -> assertThat(result.userId()).isEqualTo(100L),
                    () -> assertThat(result.status()).isEqualTo("PENDING"));
        }

        @DisplayName("Redis에서 수량 초과 시 Service를 호출하지 않고 즉시 거절한다")
        @Test
        void issueCoupon_whenQuantityExhausted() {
            // arrange
            when(couponIssueLimiter.tryIssue(1L, 200L))
                    .thenReturn(CouponIssueResult.QUANTITY_EXHAUSTED);

            // act & assert
            assertThatThrownBy(() -> couponFacade.issueCoupon(1L, 200L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.QUANTITY_EXHAUSTED));
            verify(couponService, never()).issue(anyLong(), anyLong());
        }

        @DisplayName("Redis에서 중복 발급 감지 시 ALREADY_ISSUED 예외를 던진다")
        @Test
        void issueCoupon_whenAlreadyIssued() {
            // arrange
            when(couponIssueLimiter.tryIssue(1L, 100L))
                    .thenReturn(CouponIssueResult.ALREADY_ISSUED);

            // act & assert
            assertThatThrownBy(() -> couponFacade.issueCoupon(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.ALREADY_ISSUED));
            verify(couponService, never()).issue(anyLong(), anyLong());
        }

        @DisplayName("Outbox 저장 실패 시 Redis에서 ZREM으로 롤백한다")
        @Test
        void issueCoupon_whenOutboxFails_rollbacksRedis() {
            // arrange
            when(couponIssueLimiter.tryIssue(1L, 100L))
                    .thenReturn(CouponIssueResult.SUCCESS);
            org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                    .when(outboxEventPublisher).publish(any(), any(), any(), any());

            // act & assert
            assertThatThrownBy(() -> couponFacade.issueCoupon(1L, 100L))
                    .isInstanceOf(RuntimeException.class);
            verify(couponIssueLimiter).rollback(1L, 100L);
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때 (UC-C08), ")
    @Nested
    class GetMyOwnedCoupons {

        @DisplayName("CouponService에 위임하고 보유 쿠폰 목록을 반환한다")
        @Test
        void getMyOwnedCoupons_success() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "신규가입 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, ZonedDateTime.now().plusDays(30));
            OwnedCouponModel owned = OwnedCouponModel.create(coupon, 100L);
            when(couponService.getMyOwnedCoupons(100L))
                    .thenReturn(List.of(owned));

            // act
            List<CouponResult.OwnedDetail> result =
                    couponFacade.getMyOwnedCoupons(100L);

            // assert
            assertAll(
                    () -> verify(couponService).getMyOwnedCoupons(100L),
                    () -> assertThat(result).hasSize(1),
                    () -> assertThat(result.get(0).couponName()).isEqualTo("신규가입 할인"),
                    () -> assertThat(result.get(0).status()).isEqualTo("AVAILABLE"));
        }
    }
}
