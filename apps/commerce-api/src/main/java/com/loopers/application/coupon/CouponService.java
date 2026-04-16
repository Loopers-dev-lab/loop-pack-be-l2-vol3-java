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
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * 실험용 플래그
 * BYPASS_OUTBOX=true   → Outbox 없이 TX commit 후 직접 Kafka 발행
 * CRASH_BEFORE_SEND=true → TX commit 완료 후, Kafka send 직전 크래시 시뮬레이션 → 이벤트 유실 재현
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponService {

    private static final boolean BYPASS_OUTBOX =
        Boolean.parseBoolean(System.getenv().getOrDefault("BYPASS_OUTBOX", "false"));

    private static final boolean CRASH_BEFORE_SEND =
        Boolean.parseBoolean(System.getenv().getOrDefault("CRASH_BEFORE_SEND", "false"));

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
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

        if (BYPASS_OUTBOX) {
            // [실험 3] Outbox 없이 직접 발행 — TX commit 후 afterCommit()에서 Kafka 발행 시도
            // DB commit과 Kafka send가 원자적이지 않음 → 그 사이 크래시 시 이벤트 유실
            String payload = objectMapper.writeValueAsString(event);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (CRASH_BEFORE_SEND) {
                        log.error("[Bypass] CRASH_BEFORE_SEND — DB commit 완료, Kafka send 전 크래시! eventId={}", eventId);
                        throw new RuntimeException("simulated crash before Kafka send");
                    }
                    try {
                        kafkaTemplate.send(KafkaTopics.COUPON_ISSUE_REQUESTS, String.valueOf(couponTemplateId), objectMapper.readTree(payload));
                        log.info("[Bypass] direct Kafka send eventId={}", eventId);
                    } catch (Exception e) {
                        log.error("[Bypass] Kafka send failed eventId={}", eventId, e);
                    }
                }
            });
            return eventId;
        }

        outboxEventRepository.save(OutboxEvent.create(eventId, KafkaTopics.COUPON_ISSUE_REQUESTS, String.valueOf(couponTemplateId), objectMapper.writeValueAsString(event)));
        return eventId; // 클라이언트가 결과 polling 시 사용
    }
}
