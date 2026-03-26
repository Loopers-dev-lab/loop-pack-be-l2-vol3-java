package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.CouponIssueRequestMessage;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.eventhandled.EventHandledService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponService couponService;
    private final EventHandledService eventHandledService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "coupon-issue-requests",
            groupId = "coupon-issue-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<CouponIssueRequestMessage> messages, Acknowledgment acknowledgment) {
        for (CouponIssueRequestMessage message : messages) {
            try {
                processMessage(message);
            } catch (Exception e) {
                log.error("쿠폰 발급 이벤트 처리 실패: eventId={}", message.eventId(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processMessage(CouponIssueRequestMessage message) {
        if (eventHandledService.isAlreadyHandled(message.eventId())) {
            log.debug("이미 처리된 이벤트: eventId={}", message.eventId());
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            Long userId = payload.get("userId").asLong();
            Long couponId = payload.get("couponId").asLong();

            couponService.issueCouponWithQuantityControl(userId, couponId);
            eventHandledService.markAsHandled(message.eventId());
        } catch (JsonProcessingException e) {
            log.error("페이로드 파싱 실패: eventId={}", message.eventId(), e);
        }
    }
}
