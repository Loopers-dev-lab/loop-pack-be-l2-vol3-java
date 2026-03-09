package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    @DisplayName("validateAndUse는 첫 시도 OptimisticLockException 발생 시 1회 재시도 후 성공한다.")
    void validateAndUse_whenFirstAttemptOptimisticLock_thenRetriesOnceAndSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        CouponService sut = new TestableCouponService(couponTemplateRepository, issuedCouponRepository, calls,
                new com.loopers.domain.coupon.CouponDiscount(
                        BigDecimal.valueOf(10000),
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(9000)
                ));

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
        CouponService sut = new TestableCouponService(couponTemplateRepository, issuedCouponRepository, calls, null);

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
                              AtomicInteger calls,
                              CouponDiscount successResult) {
            super(couponTemplateRepository, issuedCouponRepository);
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

