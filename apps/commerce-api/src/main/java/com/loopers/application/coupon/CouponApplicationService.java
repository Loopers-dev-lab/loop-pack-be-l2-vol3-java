package com.loopers.application.coupon;

import com.loopers.application.coupon.event.CouponIssueRequestedEvent;
import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.FcfsCoupon;
import com.loopers.domain.coupon.FcfsCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final FcfsCouponRepository fcfsCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;
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

    @Transactional
    public FcfsCoupon registerFcfsCoupon(Long couponId, int maxQuantity, ZonedDateTime openedAt, ZonedDateTime closedAt) {
        couponDomainService.getById(couponId);
        FcfsCoupon fcfsCoupon = new FcfsCoupon(couponId, maxQuantity, openedAt, closedAt);
        return fcfsCouponRepository.save(fcfsCoupon);
    }

    public CouponIssueRequest requestFcfsCouponIssue(Long couponId, Long userId) {
        try {
            return executeInNewTransaction(() -> {
                FcfsCoupon fcfsCoupon = fcfsCouponRepository.findByCouponId(couponId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "선착순 쿠폰을 찾을 수 없습니다."));
                fcfsCoupon.validateIssuable();

                couponIssueRequestRepository.findByCouponIdAndUserId(couponId, userId)
                    .ifPresent(existing -> {
                        throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다.");
                    });

                CouponIssueRequest request = new CouponIssueRequest(couponId, userId);
                couponIssueRequestRepository.save(request);

                eventPublisher.publishEvent(new CouponIssueRequestedEvent(
                    request.getRequestId(), couponId, userId, ZonedDateTime.now()));

                return request;
            });
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다.");
        }
    }

    @Transactional(readOnly = true)
    public CouponIssueRequest getCouponIssueRequestStatus(String requestId, Long userId) {
        return couponIssueRequestRepository.findByRequestIdAndUserId(requestId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
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
