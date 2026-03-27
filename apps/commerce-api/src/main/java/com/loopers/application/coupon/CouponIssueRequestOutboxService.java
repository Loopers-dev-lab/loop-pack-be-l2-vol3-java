package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.outbox.DomainEventTypes;
import com.loopers.domain.outbox.DomainKafkaTopics;
import com.loopers.domain.outbox.TransactionalOutboxWriter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class CouponIssueRequestOutboxService {

    private final CouponService couponService;
    private final TransactionalOutboxWriter transactionalOutboxWriter;
    private final CouponIssueRequestRepository couponIssueRequestRepository;

    public CouponIssueRequestOutboxService(
            CouponService couponService,
            TransactionalOutboxWriter transactionalOutboxWriter,
            CouponIssueRequestRepository couponIssueRequestRepository
    ) {
        this.couponService = couponService;
        this.transactionalOutboxWriter = transactionalOutboxWriter;
        this.couponIssueRequestRepository = couponIssueRequestRepository;
    }

    @Transactional
    public CouponIssueRequestInfo request(Long userId, Long couponTemplateId) {
        var template = couponService.findTemplateByIdAndNotDeleted(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (template.isExpired(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        String requestId = UUID.randomUUID().toString();
        CouponIssueRequestModel row = CouponIssueRequestModel.pending(requestId, userId, couponTemplateId);
        couponIssueRequestRepository.save(row);

        transactionalOutboxWriter.record(
                requestId,
                DomainKafkaTopics.COUPON_ISSUE_REQUESTS,
                String.valueOf(couponTemplateId),
                DomainEventTypes.COUPON_ISSUE_REQUESTED,
                Map.of(
                        "requestId", requestId,
                        "userId", userId,
                        "couponTemplateId", couponTemplateId
                )
        );
        return CouponIssueRequestInfo.from(row);
    }
}
