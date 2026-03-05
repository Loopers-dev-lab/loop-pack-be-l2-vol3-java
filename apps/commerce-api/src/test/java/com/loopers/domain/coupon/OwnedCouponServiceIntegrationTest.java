package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.coupon.discount.CouponDiscount;
import com.loopers.domain.shared.Money;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.ConcurrentTestHelper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OwnedCouponServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OwnedCouponService ownedCouponService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OwnedCouponRepository ownedCouponRepository;

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰을 발급하면, 보유 쿠폰이 DB에 저장된다.")
        @Test
        void savesOwnedCouponToDatabase_whenValidCouponProvided() {
            // arrange
            var coupon = couponService.create(new CouponTerms("발급 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var userId = 1L;

            // act
            var result = ownedCouponService.issue(coupon.getId(), userId);

            // assert
            assertAll(
                    () -> assertThat(result.getCoupon().getId()).isEqualTo(coupon.getId()),
                    () -> assertThat(result.getUserId()).isEqualTo(userId),
                    () -> assertThat(result.getStatus()).isEqualTo("AVAILABLE")
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponNotFound() {
            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(999L, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }

        @DisplayName("삭제된 쿠폰을 발급하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsDeleted() {
            // arrange
            var coupon = couponService.create(new CouponTerms("삭제 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            couponService.delete(coupon.getId());

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }

        @DisplayName("만료된 쿠폰을 발급하면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsExpired() {
            // arrange
            var coupon = couponService.create(new CouponTerms("만료 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            ReflectionTestUtils.setField(coupon, "expiredAt", ZonedDateTime.now().minusDays(1));
            couponRepository.save(coupon);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.EXPIRED_COUPON));
        }

        @DisplayName("이미 발급받은 쿠폰을 중복 발급하면, DUPLICATE_COUPON_ISSUE 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateIssue() {
            // arrange
            var coupon = couponService.create(new CouponTerms("중복 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            ownedCouponService.issue(coupon.getId(), 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_COUPON_ISSUED));
        }
    }

    @DisplayName("주문에 쿠폰을 적용할 때,")
    @Nested
    class ApplyForOrder {

        @DisplayName("유효한 쿠폰이면, 할인이 적용되고 USED로 변경된다.")
        @Test
        void appliesDiscountAndChangesStatusToUsed() {
            // arrange
            var coupon = couponService.create(new CouponTerms("5000원 할인", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);

            // act
            var result = ownedCouponService.applyCoupon(ownedCoupon.getId(), 1L, Money.wons(20000L));

            // assert
            var saved = ownedCouponRepository.findByIdWithCoupon(ownedCoupon.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.discountAmount()).isEqualTo(Money.wons(5000L)),
                    () -> assertThat(result.ownedCouponId()).isEqualTo(ownedCoupon.getId()),
                    () -> assertThat(saved.getStatus()).isEqualTo("USED")
            );
        }

        @DisplayName("쿠폰 ID가 null이면, 할인 없이 CouponDiscount.NONE을 반환한다.")
        @Test
        void returnsNone_whenOwnedCouponIdIsNull() {
            // act
            CouponDiscount result = ownedCouponService.applyCoupon(null, 1L, Money.wons(20000L));

            // assert
            assertAll(
                    () -> assertThat(result.discountAmount()).isEqualTo(Money.ZERO),
                    () -> assertThat(result.ownedCouponId()).isNull()
            );
        }

        @DisplayName("존재하지 않는 보유 쿠폰이면, OWNED_COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenOwnedCouponNotFound() {
            assertThatThrownBy(() -> ownedCouponService.applyCoupon(999L, 1L, Money.wons(20000L)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.OWNED_COUPON_NOT_FOUND));
        }

        @DisplayName("타인 소유의 쿠폰이면, FORBIDDEN_COUPON_ACCESS 예외가 발생한다.")
        @Test
        void throwsException_whenNotOwner() {
            // arrange
            var coupon = couponService.create(new CouponTerms("할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.applyCoupon(ownedCoupon.getId(), 999L, Money.wons(20000L)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN_COUPON_ACCESS));
        }

        @DisplayName("이미 사용된 쿠폰이면, ALREADY_USED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyUsed() {
            // arrange
            var coupon = couponService.create(new CouponTerms("할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);
            ownedCouponService.applyCoupon(ownedCoupon.getId(), 1L, Money.wons(20000L));

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.applyCoupon(ownedCoupon.getId(), 1L, Money.wons(20000L)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_USED_COUPON));
        }

        @DisplayName("만료된 쿠폰을 적용하면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsExpired() {
            // arrange
            var coupon = couponService.create(new CouponTerms("만료 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);
            ReflectionTestUtils.setField(coupon, "expiredAt", ZonedDateTime.now().minusDays(1));
            couponRepository.save(coupon);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.applyCoupon(ownedCoupon.getId(), 1L, Money.wons(20000L)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.EXPIRED_COUPON));
        }

        @DisplayName("최소 주문 금액 미달이면, COUPON_MIN_ORDER_PRICE_NOT_MET 예외가 발생한다.")
        @Test
        void throwsException_whenMinOrderPriceNotMet() {
            // arrange
            var coupon = couponService.create(new CouponTerms("할인 쿠폰", CouponType.FIXED, 5000L, null, 20000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.applyCoupon(ownedCoupon.getId(), 1L, Money.wons(10000L)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_MIN_ORDER_PRICE_NOT_MET));
        }

        @DisplayName("동시에 같은 쿠폰을 사용하면, 하나만 성공하고 나머지는 OptimisticLockingFailureException 또는 ALREADY_USED_COUPON 예외가 발생한다.")
        @Test
        void onlyOneSucceeds_whenConcurrentApply() throws InterruptedException {
            // arrange
            var coupon = couponService.create(new CouponTerms("동시성 쿠폰", CouponType.FIXED, 1000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);
            int threadCount = 5;

            // act
            var result = ConcurrentTestHelper.executeConcurrently(
                    threadCount,
                    () -> ownedCouponService.applyCoupon(ownedCoupon.getId(), 1L, Money.wons(20000L))
            );

            // assert
            assertAll(
                    () -> assertThat(result.successCount()).isEqualTo(1),
                    () -> assertThat(result.exceptions()).hasSize(threadCount - 1),
                    () -> assertThat(result.exceptions()).allSatisfy(e ->
                            assertThat(e).isInstanceOfAny(
                                    ObjectOptimisticLockingFailureException.class,
                                    CoreException.class
                            )
                    ),
                    () -> assertThat(result.exceptions())
                            .filteredOn(e -> e instanceof CoreException)
                            .allSatisfy(e ->
                                    assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_USED_COUPON)
                            )
            );
        }
    }
}
