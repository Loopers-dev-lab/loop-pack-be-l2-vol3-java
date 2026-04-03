package com.loopers.application.coupon;

import com.loopers.application.metrics.ProductMetricsAckPublisher;
import com.loopers.contract.coupon.CouponIssueRequestedEvent;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponIssueConsumeApplicationService {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final ProductMetricsAckPublisher productMetricsAckPublisher;

    @Transactional
    public void consume(String consumerGroup, CouponIssueRequestedEvent event) {
        int processingMarked = couponIssueRequestJpaRepository.markProcessing(
                event.requestId(),
                CouponIssueRequestStatus.PENDING,
                CouponIssueRequestStatus.PROCESSING
        );
        if (processingMarked == 0) {
            productMetricsAckPublisher.publish(event.requestId(), consumerGroup);
            return;
        }

        Optional<CouponEntity> couponOptional = couponJpaRepository.findByIdAndDeletedAtIsNull(event.couponId());
        if (couponOptional.isEmpty()) {
            couponIssueRequestJpaRepository.markFailed(event.requestId(), CouponIssueRequestStatus.FAILED_COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다.", LocalDateTime.now());
            productMetricsAckPublisher.publish(event.requestId(), consumerGroup);
            return;
        }

        CouponEntity coupon = couponOptional.get();
        if (LocalDateTime.now().isAfter(coupon.getExpiredAt())) {
            couponIssueRequestJpaRepository.markFailed(event.requestId(), CouponIssueRequestStatus.FAILED_EXPIRED, "만료된 쿠폰입니다.", LocalDateTime.now());
            productMetricsAckPublisher.publish(event.requestId(), consumerGroup);
            return;
        }

        int decreased = couponJpaRepository.decreaseRemainingQuantityAtomically(event.couponId(), 1);
        if (decreased == 0) {
            couponIssueRequestJpaRepository.markFailed(event.requestId(), CouponIssueRequestStatus.FAILED_SOLD_OUT, "쿠폰 잔여 수량이 없습니다.", LocalDateTime.now());
            productMetricsAckPublisher.publish(event.requestId(), consumerGroup);
            return;
        }

        try {
            issuedCouponJpaRepository.save(new IssuedCouponEntity(
                    event.memberId(),
                    event.couponId(),
                    CouponStatus.AVAILABLE,
                    LocalDateTime.now(),
                    coupon.getExpiredAt(),
                    null
            ));
        } catch (DataIntegrityViolationException e) {
            couponJpaRepository.increaseRemainingQuantityAtomically(event.couponId(), 1);
            couponIssueRequestJpaRepository.markFailed(event.requestId(), CouponIssueRequestStatus.FAILED_DUPLICATE_ISSUANCE, "이미 발급된 쿠폰입니다.", LocalDateTime.now());
            productMetricsAckPublisher.publish(event.requestId(), consumerGroup);
            return;
        }

        couponIssueRequestJpaRepository.markSucceeded(event.requestId(), CouponIssueRequestStatus.SUCCEEDED, LocalDateTime.now());
        productMetricsAckPublisher.publish(event.requestId(), consumerGroup);
    }
}
