package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * CouponService 낙관락 재시도 동작 단위 테스트.
 * REQUIRES_NEW 트랜잭션 메서드를 감싸는 validateAndUse의 재시도·예외 변환을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceRetryTest {

    @Mock
    private CouponTemplateRepository couponTemplateRepository;
    @Mock
    private IssuedCouponRepository issuedCouponRepository;
    @Mock
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Test
    @DisplayName("issue는 DB UNIQUE 제약 충돌(DataIntegrityViolationException)을 CONFLICT(409)로 변환한다.")
    void issue_whenUniqueConstraintViolation_thenThrowsConflict() {
        CouponService sut = new CouponService(couponTemplateRepository, issuedCouponRepository,
                couponIssueRequestRepository);
        CouponTemplateModel template = CouponTemplateModel.create(
                "중복발급", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(3), 100);

        when(couponTemplateRepository.findByIdAndNotDeletedForUpdate(eq(1L)))
                .thenReturn(java.util.Optional.of(template));
        when(issuedCouponRepository.existsByUserIdAndCouponId(eq(10L), eq(1L)))
                .thenReturn(false);
        when(issuedCouponRepository.save(any(IssuedCouponModel.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        CoreException ex = assertThrows(CoreException.class, () -> sut.issue(10L, 1L));
        assertThat(ex.getErrorType()).isEqualTo(com.loopers.support.error.ErrorType.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("이미 발급받은 쿠폰입니다.");
    }

    @Test
    @DisplayName("validateAndUse는 첫 시도 OptimisticLockException 발생 시 1회 재시도 후 성공한다.")
    void validateAndUse_whenFirstAttemptOptimisticLock_thenRetriesOnceAndSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        CouponService sut = new TestableCouponService(couponTemplateRepository, issuedCouponRepository,
                couponIssueRequestRepository, calls,
                new com.loopers.domain.coupon.CouponDiscount(
                        BigDecimal.valueOf(10000),
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(9000)));

        CouponDiscount result = sut.validateAndUse(1L, 1L, BigDecimal.valueOf(10000));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(result.beforeAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(result.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(result.afterAmount()).isEqualByComparingTo(BigDecimal.valueOf(9000));
    }

    @Test
    @DisplayName("validateAndUse는 두 번 모두 OptimisticLockException이면 CONFLICT(409) + '잠시 후 다시 시도해 주세요'를 던진다.")
    void validateAndUse_whenBothAttemptsOptimisticLock_thenThrowsConflict() {
        AtomicInteger calls = new AtomicInteger();
        CouponService sut = new TestableCouponService(couponTemplateRepository, issuedCouponRepository,
                couponIssueRequestRepository, calls, null);

        CoreException ex = assertThrows(CoreException.class,
                () -> sut.validateAndUse(1L, 1L, BigDecimal.valueOf(10000)));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(ex).isInstanceOf(CoreException.class);
        assertThat(ex.getMessage()).isEqualTo("잠시 후 다시 시도해 주세요.");
    }

    /**
     * 내부 REQUIRES_NEW 메서드를 오버라이드해 낙관락 예외/성공을 시뮬레이션하는 테스트용 서브클래스.
     */
    static class TestableCouponService extends CouponService {

        private final AtomicInteger calls;
        private final CouponDiscount successResult;

        TestableCouponService(CouponTemplateRepository couponTemplateRepository,
                IssuedCouponRepository issuedCouponRepository,
                CouponIssueRequestRepository couponIssueRequestRepository,
                AtomicInteger calls,
                CouponDiscount successResult) {
            super(couponTemplateRepository, issuedCouponRepository, couponIssueRequestRepository);
            this.calls = calls;
            this.successResult = successResult;
        }

        @Override
        CouponDiscount validateAndUseInNewTransaction(Long issuedCouponId,
                Long userId,
                BigDecimal orderAmountBeforeDiscount) {
            int attempt = calls.getAndIncrement();
            if (attempt == 0) {
                throw new OptimisticLockException("version conflict");
            }
            if (successResult == null) {
                throw new OptimisticLockException("version conflict");
            }
            return successResult;
        }
    }
}
