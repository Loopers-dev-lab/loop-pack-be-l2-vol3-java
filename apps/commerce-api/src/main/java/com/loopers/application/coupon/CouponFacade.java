package com.loopers.application.coupon;

import com.loopers.application.coupon.dto.CouponCriteria;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.application.coupon.event.CouponIssuedMessage;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.CouponErrorCode;
import com.loopers.domain.coupon.CouponIssueLimiter;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.infrastructure.outbox.OutboxEventPublisher;
import com.loopers.support.error.CoreException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;
    private final CouponIssueLimiter couponIssueLimiter;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public CouponResult.Detail registerCoupon(CouponCriteria.Create criteria) {
        CouponModel coupon = couponService.register(criteria.toCommand());
        couponIssueLimiter.registerTotalQuantity(coupon.getId(), coupon.getTotalQuantity());
        return CouponResult.Detail.from(coupon, 0);
    }

    @Transactional(readOnly = true)
    public CouponResult.Detail getCoupon(Long couponId) {
        return CouponResult.Detail.from(
                couponService.getById(couponId),
                couponService.countIssuedCoupons(couponId));
    }

    @Transactional(readOnly = true)
    public Page<CouponResult.Detail> getCoupons(Pageable pageable) {
        Page<CouponModel> coupons = couponService.getAll(pageable);
        List<Long> couponIds = coupons.getContent().stream()
                .map(CouponModel::getId)
                .toList();
        Map<Long, Long> issuedCountMap = couponService.countIssuedCoupons(couponIds);
        return coupons.map(coupon -> CouponResult.Detail.from(
                coupon, issuedCountMap.getOrDefault(coupon.getId(), 0L)));
    }

    @Transactional
    public void updateCoupon(Long couponId, CouponCriteria.Update criteria) {
        couponService.update(couponId, criteria.toCommand());
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        couponService.delete(couponId);
    }

    @Transactional(readOnly = true)
    public Page<CouponResult.IssuedDetail> getIssuedCoupons(Long couponId, Pageable pageable) {
        return couponService.getIssuedCoupons(couponId, pageable)
                .map(CouponResult.IssuedDetail::from);
    }

    @Transactional
    public CouponResult.IssuedDetail issueCoupon(Long couponId, Long userId) {
        CouponIssueResult result = couponIssueLimiter.tryIssue(couponId, userId);
        if (result == CouponIssueResult.NOT_FOUND) {
            throw new CoreException(CouponErrorCode.NOT_FOUND);
        }
        if (result == CouponIssueResult.ALREADY_ISSUED) {
            throw new CoreException(CouponErrorCode.ALREADY_ISSUED);
        }
        if (result == CouponIssueResult.QUANTITY_EXHAUSTED) {
            throw new CoreException(CouponErrorCode.QUANTITY_EXHAUSTED);
        }

        try {
            outboxEventPublisher.publish(
                    "COUPON_ISSUED",
                    KafkaTopics.COUPON_ISSUED,
                    String.valueOf(couponId),
                    new CouponIssuedMessage(couponId, userId, System.currentTimeMillis()));
            return CouponResult.IssuedDetail.pending(couponId, userId);
        } catch (Exception e) {
            couponIssueLimiter.rollback(couponId, userId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<CouponResult.OwnedDetail> getMyOwnedCoupons(Long userId) {
        return couponService.getMyOwnedCoupons(userId).stream()
                .map(CouponResult.OwnedDetail::from)
                .toList();
    }
}
