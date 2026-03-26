package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CouponServiceUnitTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponService couponService;

    private final ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

    @DisplayName("쿠폰 템플릿을 등록할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 정보면, 정상적으로 등록된다.")
        @Test
        void registerSuccess() {
            // given
            when(couponRepository.save(any(CouponModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponModel result = couponService.register(new CouponService.RegisterCommand(
                    "3000원 할인", CouponType.FIXED, BigDecimal.valueOf(3000), null, expiredAt, 0));

            // then
            assertAll(
                    () -> assertThat(result.getName()).isEqualTo("3000원 할인"),
                    () -> assertThat(result.getType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(result.getDiscountAmount().amount()).isEqualByComparingTo(BigDecimal.valueOf(3000))
            );
            verify(couponRepository, times(1)).save(any(CouponModel.class));
        }

        @DisplayName("이름이 null이면, save가 호출되지 않고 예외가 발생한다.")
        @Test
        void failWithNullName() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    couponService.register(new CouponService.RegisterCommand(
                            null, CouponType.FIXED, BigDecimal.valueOf(3000), null, expiredAt, 0))
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(couponRepository, never()).save(any(CouponModel.class));
        }
    }

    @DisplayName("쿠폰 템플릿을 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID면, 쿠폰이 반환된다.")
        @Test
        void getByIdSuccess() {
            // given
            CouponModel coupon = new CouponModel("3000원 할인", CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, null, expiredAt, 0);
            when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

            // when
            CouponModel result = couponService.getById(1L);

            // then
            assertThat(result.getName()).isEqualTo("3000원 할인");
        }

        @DisplayName("존재하지 않는 ID면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundId() {
            // given
            when(couponRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponService.getById(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때,")
    @Nested
    class GetAll {

        @DisplayName("등록된 쿠폰 목록이 페이징되어 반환된다.")
        @Test
        void getAllSuccess() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            List<CouponModel> coupons = List.of(
                    new CouponModel("3000원 할인", CouponType.FIXED, BigDecimal.valueOf(3000), null, null, expiredAt, 0),
                    new CouponModel("10% 할인", CouponType.RATE, null, 10, null, expiredAt, 0)
            );
            when(couponRepository.findAll(pageable)).thenReturn(new PageImpl<>(coupons, pageable, coupons.size()));

            // when
            Page<CouponModel> result = couponService.getAll(pageable);

            // then
            assertThat(result.getContent()).hasSize(2);
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 ID면, 정상적으로 삭제된다.")
        @Test
        void deleteSuccess() {
            // given
            CouponModel coupon = new CouponModel("3000원 할인", CouponType.FIXED,
                    BigDecimal.valueOf(3000), null, null, expiredAt, 0);
            when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

            // when
            couponService.delete(1L);

            // then
            verify(couponRepository, times(1)).delete(coupon);
        }

        @DisplayName("존재하지 않는 ID면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundId() {
            // given
            when(couponRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponService.delete(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
