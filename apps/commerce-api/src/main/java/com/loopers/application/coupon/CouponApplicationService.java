package com.loopers.application.coupon;

import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;

@RequiredArgsConstructor
@Component
public class CouponApplicationService {

    private final CouponDomainService couponDomainService;
    private final CouponIssueDomainService couponIssueDomainService;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_RETRY = 3;

    public CouponIssue issueCoupon(Long couponId, Long userId) {
        return retryOnConflict(() -> executeInNewTransaction(() -> {
            Coupon coupon = couponDomainService.getById(couponId);
            return couponIssueDomainService.issue(coupon, userId);
        }));
    }

    @Transactional(readOnly = true)
    public List<CouponIssue> getMyIssues(Long userId) {
        return couponIssueDomainService.getMyIssues(userId);
    }

    @Transactional
    public Coupon registerCoupon(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        return couponDomainService.register(name, type, value, minOrderAmount, expiredAt);
    }

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long id) {
        return couponDomainService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResult<Coupon> getAllCoupons(int page, int size) {
        return couponDomainService.getAll(page, size);
    }

    @Transactional
    public Coupon updateCoupon(Long id, String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        return couponDomainService.update(id, name, type, value, minOrderAmount, expiredAt);
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponDomainService.delete(id);
    }

    @Transactional(readOnly = true)
    public PageResult<CouponIssue> getCouponIssues(Long couponId, int page, int size) {
        return couponIssueDomainService.getIssuesByCouponId(couponId, page, size);
    }

    private <T> T retryOnConflict(Supplier<T> operation) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException e) {
                if (i == MAX_RETRY - 1) throw e;
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private <T> T executeInNewTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }
}
