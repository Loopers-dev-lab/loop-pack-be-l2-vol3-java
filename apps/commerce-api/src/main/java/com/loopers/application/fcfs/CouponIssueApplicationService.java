package com.loopers.application.fcfs;

import com.loopers.application.event.CouponIssueRequestCreatedEvent;
import com.loopers.domain.fcfs.CouponIssueRequest;
import com.loopers.domain.fcfs.CouponIssueRequestRepository;
import com.loopers.domain.fcfs.FcfsCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 선착순 쿠폰 발급 Application Service.
 */
@Service
@RequiredArgsConstructor
public class CouponIssueApplicationService {

    private final FcfsCouponRepository fcfsCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 쿠폰 발급 요청.
     * 1. 쿠폰 존재 여부 확인
     * 2. 발급 요청 저장 (PENDING)
     * 3. 이벤트 발행 → Outbox 패턴으로 Kafka에 전달
     */
    @Transactional
    public CouponIssueRequestResult requestIssue(Long couponId, Long userId) {
        // 1. 쿠폰 존재 확인
        if (!fcfsCouponRepository.existsById(couponId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "Coupon not found: " + couponId);
        }

        // 2. 발급 요청 저장
        CouponIssueRequest request = CouponIssueRequest.create(couponId, userId);
        CouponIssueRequest savedRequest = couponIssueRequestRepository.save(request);

        // 3. 이벤트 발행 (OutboxEventListener가 Outbox에 저장)
        String eventId = UUID.randomUUID().toString();
        eventPublisher.publishEvent(CouponIssueRequestCreatedEvent.of(
            eventId,
            savedRequest.getRequestId(),
            couponId,
            userId
        ));

        return CouponIssueRequestResult.of(savedRequest.getRequestId(), savedRequest.getStatus());
    }

    /**
     * 발급 결과 조회.
     */
    @Transactional(readOnly = true)
    public CouponIssueResultResponse getIssueResult(String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "Issue request not found: " + requestId));

        return CouponIssueResultResponse.of(
            request.getRequestId(),
            request.getStatus(),
            request.getFailureReason()
        );
    }
}
