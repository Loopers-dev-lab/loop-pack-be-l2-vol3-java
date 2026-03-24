package com.loopers.application.coupon;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CouponIssueApp {

    private static final String COUPON_ISSUE_TOPIC = "coupon-issue-requests";

    private final CouponService couponService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final OutboxAppender outboxAppender;

    @Transactional
    public CouponIssueRequestInfo requestIssue(Long couponTemplateId, Long memberId) {
        CouponTemplateModel template = couponService.findActiveTemplate(couponTemplateId);
        if (!template.isQuantityLimited()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰이 아닙니다.");
        }

        boolean alreadyActive = couponIssueRequestRepository
                .existsByRefCouponTemplateIdAndRefMemberIdAndStatusIn(
                        new RefCouponTemplateId(couponTemplateId),
                        new RefMemberId(memberId),
                        List.of(CouponIssueStatus.PENDING, CouponIssueStatus.ISSUED));
        if (alreadyActive) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청 중이거나 발급된 쿠폰입니다.");
        }

        CouponIssueRequestModel request = CouponIssueRequestModel.create(couponTemplateId, memberId);
        couponIssueRequestRepository.save(request);

        CouponIssueOutboxPayload payload = new CouponIssueOutboxPayload(
                UUID.randomUUID().toString(), "CouponIssueRequested", 1,
                request.getRequestId(), couponTemplateId, memberId, LocalDateTime.now());
        outboxAppender.append(
                "coupon_issue_request", String.valueOf(couponTemplateId),
                "CouponIssueRequested", COUPON_ISSUE_TOPIC, payload);

        return CouponIssueRequestInfo.from(request);
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueStatus(String requestId, Long memberId) {
        CouponIssueRequestModel request = couponIssueRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        if (!request.getRefMemberId().value().equals(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 발급 요청만 조회할 수 있습니다.");
        }
        return CouponIssueRequestInfo.from(request);
    }
}
