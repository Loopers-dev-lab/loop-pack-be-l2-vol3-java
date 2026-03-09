package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.coupon.CreateCouponCommand;
import org.instancio.Instancio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    @Nested
    @DisplayName("쿠폰 생성")
    class CreateCoupon {

        @Test
        @DisplayName("성공: 정액 할인 쿠폰을 생성한다")
        void createCoupon_Fixed_Success() {
            // Given
            String name = "신규회원 5000원 할인";
            CouponType type = CouponType.FIXED;
            BigDecimal value = new BigDecimal("5000");
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            given(couponRepository.save(any(Coupon.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            Coupon result = couponService.createCoupon(new CreateCouponCommand(name, type, value, null, expiredAt));

            // Then
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getType()).isEqualTo(CouponType.FIXED);
            assertThat(result.getValue()).isEqualByComparingTo(value);
            then(couponRepository).should().save(any(Coupon.class));
        }

        @Test
        @DisplayName("성공: 비율 할인 쿠폰을 생성한다")
        void createCoupon_Rate_Success() {
            // Given
            String name = "10% 할인 쿠폰";
            CouponType type = CouponType.RATE;
            BigDecimal value = new BigDecimal("10");
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            given(couponRepository.save(any(Coupon.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            Coupon result = couponService.createCoupon(new CreateCouponCommand(name, type, value, null, expiredAt));

            // Then
            assertThat(result.getType()).isEqualTo(CouponType.RATE);
            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        @DisplayName("실패: 쿠폰명이 비어있으면 BAD_REQUEST")
        void createCoupon_BlankName() {
            // When & Then
            assertThatThrownBy(() -> couponService.createCoupon(new CreateCouponCommand("", CouponType.FIXED, new BigDecimal("1000"), null, ZonedDateTime.now().plusDays(1))))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("쿠폰명은 필수입니다.");
        }

        @Test
        @DisplayName("실패: 할인값이 0 이하면 BAD_REQUEST")
        void createCoupon_InvalidValue() {
            // When & Then
            assertThatThrownBy(() -> couponService.createCoupon(new CreateCouponCommand("쿠폰", CouponType.FIXED, BigDecimal.ZERO, null, ZonedDateTime.now().plusDays(1))))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("할인 값은 0보다 커야 합니다.");
        }

        @Test
        @DisplayName("실패: RATE 타입 할인값이 100 초과면 BAD_REQUEST")
        void createCoupon_RateExceeds100() {
            // When & Then
            assertThatThrownBy(() -> couponService.createCoupon(new CreateCouponCommand("쿠폰", CouponType.RATE, new BigDecimal("101"), null, ZonedDateTime.now().plusDays(1))))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("비율 할인은 100을 초과할 수 없습니다.");
        }

        @Test
        @DisplayName("실패: 만료일이 과거면 BAD_REQUEST")
        void createCoupon_ExpiredAtInPast() {
            // When & Then
            assertThatThrownBy(() -> couponService.createCoupon(new CreateCouponCommand("쿠폰", CouponType.FIXED, new BigDecimal("1000"), null, ZonedDateTime.now().minusDays(1))))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("만료일은 현재 시간 이후여야 합니다.");
        }
    }

    @Nested
    @DisplayName("쿠폰 발급")
    class IssueCoupon {

        @Test
        @DisplayName("성공: 쿠폰을 발급한다")
        void issueCoupon_Success() {
            // Given
            Long userId = 1L;
            Long couponId = 10L;

            Coupon coupon = Instancio.of(Coupon.class)
                    .set(field(Coupon::getId), couponId)
                    .set(field(Coupon::getExpiredAt), ZonedDateTime.now().plusDays(30))
                    .set(field(Coupon::getDeletedAt), null)
                    .create();

            given(couponRepository.findActiveById(couponId)).willReturn(Optional.of(coupon));
            given(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).willReturn(false);
            given(userCouponRepository.save(any(UserCoupon.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            UserCoupon result = couponService.issueCoupon(userId, couponId);

            // Then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getCouponId()).isEqualTo(couponId);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @Test
        @DisplayName("실패: 이미 발급받은 쿠폰이면 CONFLICT")
        void issueCoupon_Duplicate() {
            // Given
            Long userId = 1L;
            Long couponId = 10L;

            Coupon coupon = Instancio.of(Coupon.class)
                    .set(field(Coupon::getId), couponId)
                    .set(field(Coupon::getExpiredAt), ZonedDateTime.now().plusDays(30))
                    .set(field(Coupon::getDeletedAt), null)
                    .create();

            given(couponRepository.findActiveById(couponId)).willReturn(Optional.of(coupon));
            given(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT)
                    .hasMessage("이미 발급받은 쿠폰입니다.");
        }

        @Test
        @DisplayName("실패: 만료된 쿠폰이면 BAD_REQUEST")
        void issueCoupon_Expired() {
            // Given
            Long userId = 1L;
            Long couponId = 10L;

            Coupon coupon = Instancio.of(Coupon.class)
                    .set(field(Coupon::getId), couponId)
                    .set(field(Coupon::getExpiredAt), ZonedDateTime.now().minusDays(1))
                    .set(field(Coupon::getDeletedAt), null)
                    .create();

            given(couponRepository.findActiveById(couponId)).willReturn(Optional.of(coupon));

            // When & Then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("만료된 쿠폰은 발급할 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("쿠폰 사용")
    class UseUserCoupon {

        @Test
        @DisplayName("성공: 쿠폰을 사용한다")
        void useUserCoupon_Success() {
            // Given
            Long userCouponId = 1L;
            Long userId = 1L;
            Long couponId = 10L;
            BigDecimal orderAmount = new BigDecimal("50000");

            UserCoupon userCoupon = Instancio.of(UserCoupon.class)
                    .set(field(UserCoupon::getId), userCouponId)
                    .set(field(UserCoupon::getUserId), userId)
                    .set(field(UserCoupon::getCouponId), couponId)
                    .set(field(UserCoupon::getStatus), CouponStatus.AVAILABLE)
                    .create();

            Coupon coupon = Instancio.of(Coupon.class)
                    .set(field(Coupon::getId), couponId)
                    .set(field(Coupon::getType), CouponType.FIXED)
                    .set(field(Coupon::getValue), new BigDecimal("5000"))
                    .set(field(Coupon::getMinOrderAmount), null)
                    .set(field(Coupon::getExpiredAt), ZonedDateTime.now().plusDays(30))
                    .set(field(Coupon::getDeletedAt), null)
                    .create();

            given(userCouponRepository.findByIdWithLock(userCouponId)).willReturn(Optional.of(userCoupon));
            given(couponRepository.findActiveById(couponId)).willReturn(Optional.of(coupon));

            // When
            BigDecimal discountAmount = couponService.useUserCoupon(userCouponId, userId, orderAmount);

            // Then
            assertThat(discountAmount).isEqualByComparingTo(new BigDecimal("5000"));
            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @Test
        @DisplayName("실패: 이미 사용된 쿠폰이면 BAD_REQUEST")
        void useUserCoupon_AlreadyUsed() {
            // Given
            Long userCouponId = 1L;
            Long userId = 1L;

            UserCoupon userCoupon = Instancio.of(UserCoupon.class)
                    .set(field(UserCoupon::getId), userCouponId)
                    .set(field(UserCoupon::getUserId), userId)
                    .set(field(UserCoupon::getStatus), CouponStatus.USED)
                    .create();

            given(userCouponRepository.findByIdWithLock(userCouponId)).willReturn(Optional.of(userCoupon));

            // When & Then
            assertThatThrownBy(() -> couponService.useUserCoupon(userCouponId, userId, new BigDecimal("50000")))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("사용할 수 없는 쿠폰입니다.");
        }

        @Test
        @DisplayName("실패: 다른 사용자의 쿠폰이면 FORBIDDEN")
        void useUserCoupon_OtherUser() {
            // Given
            Long userCouponId = 1L;
            Long ownerId = 1L;
            Long requestUserId = 2L;

            UserCoupon userCoupon = Instancio.of(UserCoupon.class)
                    .set(field(UserCoupon::getId), userCouponId)
                    .set(field(UserCoupon::getUserId), ownerId)
                    .set(field(UserCoupon::getStatus), CouponStatus.AVAILABLE)
                    .create();

            given(userCouponRepository.findByIdWithLock(userCouponId)).willReturn(Optional.of(userCoupon));

            // When & Then
            assertThatThrownBy(() -> couponService.useUserCoupon(userCouponId, requestUserId, new BigDecimal("50000")))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.FORBIDDEN)
                    .hasMessage("다른 사용자의 쿠폰은 사용할 수 없습니다.");
        }

        @Test
        @DisplayName("실패: 최소 주문금액 미달이면 BAD_REQUEST")
        void useUserCoupon_MinOrderAmountNotMet() {
            // Given
            Long userCouponId = 1L;
            Long userId = 1L;
            Long couponId = 10L;
            BigDecimal orderAmount = new BigDecimal("5000");

            UserCoupon userCoupon = Instancio.of(UserCoupon.class)
                    .set(field(UserCoupon::getId), userCouponId)
                    .set(field(UserCoupon::getUserId), userId)
                    .set(field(UserCoupon::getCouponId), couponId)
                    .set(field(UserCoupon::getStatus), CouponStatus.AVAILABLE)
                    .create();

            Coupon coupon = Instancio.of(Coupon.class)
                    .set(field(Coupon::getId), couponId)
                    .set(field(Coupon::getType), CouponType.FIXED)
                    .set(field(Coupon::getValue), new BigDecimal("3000"))
                    .set(field(Coupon::getMinOrderAmount), new BigDecimal("10000"))
                    .set(field(Coupon::getExpiredAt), ZonedDateTime.now().plusDays(30))
                    .set(field(Coupon::getDeletedAt), null)
                    .create();

            given(userCouponRepository.findByIdWithLock(userCouponId)).willReturn(Optional.of(userCoupon));
            given(couponRepository.findActiveById(couponId)).willReturn(Optional.of(coupon));

            // When & Then
            assertThatThrownBy(() -> couponService.useUserCoupon(userCouponId, userId, orderAmount))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("최소 주문 금액을 충족하지 않습니다.");
        }
    }

    @Nested
    @DisplayName("사용자 쿠폰 목록 조회")
    class GetUserCoupons {

        @Test
        @DisplayName("성공: 사용자의 쿠폰 목록을 조회한다")
        void getUserCoupons_Success() {
            // Given
            Long userId = 1L;
            UserCoupon userCoupon = Instancio.of(UserCoupon.class)
                    .set(field(UserCoupon::getUserId), userId)
                    .create();

            given(userCouponRepository.findAllByUserId(userId)).willReturn(List.of(userCoupon));

            // When
            List<UserCoupon> result = couponService.getUserCoupons(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(userId);
        }
    }
}
