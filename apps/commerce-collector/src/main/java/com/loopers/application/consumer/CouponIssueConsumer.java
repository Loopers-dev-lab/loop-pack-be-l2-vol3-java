package com.loopers.application.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.fcfs.CouponIssueRequestStatus;
import com.loopers.event.CouponIssueRequestedEvent;
import com.loopers.event.EventEnvelope;
import com.loopers.event.KafkaTopics;
import com.loopers.infrastructure.persistence.jpa.fcfs.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.persistence.jpa.fcfs.FcfsCouponJpaEntity;
import com.loopers.infrastructure.persistence.jpa.fcfs.FcfsCouponJpaRepository;
import com.loopers.infrastructure.persistence.jpa.fcfs.FcfsIssuedCouponJpaEntity;
import com.loopers.infrastructure.persistence.jpa.fcfs.FcfsIssuedCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 쿠폰 발급 요청을 처리하는 Kafka Consumer.
 * concurrency=1로 설정하여 같은 couponId가 같은 파티션에서 순서대로 처리되도록 함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final EventHandledRepository eventHandledRepository;
    private final FcfsCouponJpaRepository fcfsCouponJpaRepository;
    private final FcfsIssuedCouponJpaRepository fcfsIssuedCouponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.COUPON_ISSUE_REQUESTS,
        groupId = "coupon-issuer-group",
        concurrency = "1"
    )
    @Transactional
    public void consume(EventEnvelope envelope, Acknowledgment ack) {
        try {
            processEvent(envelope);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process coupon issue event: eventId={}", envelope.eventId(), e);
            // 처리 실패 시 acknowledge하지 않아 재처리됨
        }
    }

    /**
     * 이벤트 처리 (테스트 가능하도록 분리)
     */
    @Transactional
    public void processEvent(EventEnvelope envelope) {
        // 1. 멱등성 체크
        if (eventHandledRepository.existsByEventId(envelope.eventId())) {
            log.debug("Event already handled: eventId={}", envelope.eventId());
            return;
        }

        // 2. 이벤트 페이로드 파싱
        CouponIssueRequestedEvent event = parsePayload(envelope);

        // 3. 쿠폰 발급 처리 (SELECT...FOR UPDATE로 비관적 락)
        processCouponIssue(event);

        // 4. 처리 완료 기록
        eventHandledRepository.save(EventHandled.create(envelope.eventId(), envelope.eventType()));
        log.debug("Coupon issue event processed: eventId={}, requestId={}",
            envelope.eventId(), event.requestId());
    }

    private CouponIssueRequestedEvent parsePayload(EventEnvelope envelope) {
        try {
            return objectMapper.readValue(envelope.payload(), CouponIssueRequestedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse coupon issue event payload: eventId={}", envelope.eventId(), e);
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }

    private void processCouponIssue(CouponIssueRequestedEvent event) {
        String requestId = event.requestId();
        Long couponId = event.couponId();
        Long userId = event.userId();

        // 1. 쿠폰에 비관적 락 획득 (SELECT...FOR UPDATE)
        Optional<FcfsCouponJpaEntity> couponOpt = fcfsCouponJpaRepository.findByIdWithLock(couponId);

        if (couponOpt.isEmpty()) {
            updateRequestStatus(requestId, CouponIssueRequestStatus.FAILED, "쿠폰을 찾을 수 없습니다.");
            return;
        }

        FcfsCouponJpaEntity coupon = couponOpt.get();

        // 2. 중복 발급 체크
        if (fcfsIssuedCouponJpaRepository.existsByCouponIdAndUserId(couponId, userId)) {
            updateRequestStatus(requestId, CouponIssueRequestStatus.FAILED, "이미 발급받은 쿠폰입니다.");
            return;
        }

        // 3. 수량 체크
        if (!coupon.canIssue()) {
            updateRequestStatus(requestId, CouponIssueRequestStatus.FAILED, "쿠폰 수량이 소진되었습니다.");
            return;
        }

        // 4. 발급 처리
        coupon.setIssuedCount(coupon.getIssuedCount() + 1);
        fcfsCouponJpaRepository.save(coupon);

        fcfsIssuedCouponJpaRepository.save(new FcfsIssuedCouponJpaEntity(couponId, userId));

        // 5. 상태 업데이트
        updateRequestStatus(requestId, CouponIssueRequestStatus.ISSUED, null);

        log.info("Coupon issued successfully: couponId={}, userId={}, requestId={}",
            couponId, userId, requestId);
    }

    private void updateRequestStatus(String requestId, CouponIssueRequestStatus status, String failureReason) {
        couponIssueRequestJpaRepository.updateStatus(requestId, status, failureReason);
    }
}
