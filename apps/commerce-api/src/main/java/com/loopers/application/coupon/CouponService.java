package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.kafka.event.CouponIssueRequestEvent;
import com.loopers.kafka.topic.KafkaTopics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    // 기존 동기 발급 (주문 플로우에서 사용)
    @Transactional
    public CouponInfo.IssuedCouponInfo issue(Long memberId, Long couponTemplateId) {
        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        if (template.isExpired()) {
            throw new CoreException(ErrorType.COUPON_EXPIRED);
        }

        if (issuedCouponRepository.existsByMemberIdAndCouponTemplateId(memberId, couponTemplateId)) {
            throw new CoreException(ErrorType.COUPON_ALREADY_ISSUED);
        }

        IssuedCoupon issuedCoupon = issuedCouponRepository.save(new IssuedCoupon(couponTemplateId, memberId));
        return CouponInfo.IssuedCouponInfo.of(issuedCoupon, template);
    }

    // 선착순 발급: 요청을 Outbox에 저장 → OutboxPublisher가 Kafka 발행 → Consumer가 실제 발급
    // 검증(만료, 중복)은 Consumer에서 처리 → API는 빠르게 202 반환
    @SneakyThrows
    @Transactional
    public String requestIssue(Long memberId, Long couponTemplateId) {
        String eventId = UUID.randomUUID().toString();
        CouponIssueRequestEvent event = new CouponIssueRequestEvent(eventId, memberId, couponTemplateId, Instant.now().toEpochMilli());
        outboxEventRepository.save(OutboxEvent.create(eventId, KafkaTopics.COUPON_ISSUE_REQUESTS, String.valueOf(couponTemplateId), objectMapper.writeValueAsString(event)));
        return eventId; // 클라이언트가 결과 polling 시 사용
    }
}
