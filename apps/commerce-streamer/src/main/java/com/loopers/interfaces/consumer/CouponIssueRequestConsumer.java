package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueHandler;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * coupon-issue-requests 토픽 컨슈머.
 *
 * 선착순 쿠폰 발급 요청을 소비하여 CouponIssueHandler에 위임한다.
 * 처리 실패 시 DLQ로 전송. DLQ 전송도 실패하면 전체 배치 재배달.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueRequestConsumer {

    private static final String TOPIC = "coupon-issue-requests";
    private static final String DLQ_TOPIC = "coupon-issue-requests.dlq";
    private static final long DLQ_SEND_TIMEOUT_SECONDS = 5;

    private final CouponIssueHandler couponIssueHandler;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @KafkaListener(
            topics = TOPIC,
            containerFactory = KafkaConfig.BATCH_LISTENER,
            groupId = "streamer-coupon-issue-consumer"
    )
    public void consume(List<OutboxMessage> messages, Acknowledgment acknowledgment) {
        try {
            for (OutboxMessage message : messages) {
                try {
                    couponIssueHandler.handle(message);
                } catch (Exception e) {
                    log.error("[CouponIssueRequestConsumer] 처리 실패 → DLQ 전송: eventId={}, eventType={}",
                            message.eventId(), message.eventType(), e);
                    sendToDlq(message);
                }
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[CouponIssueRequestConsumer] DLQ 전송 실패 — 전체 배치 재배달 예정. error={}",
                    e.getMessage());
        }
    }

    private void sendToDlq(OutboxMessage message) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, String.valueOf(message.aggregateId()), message)
                    .get(DLQ_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.warn("[CouponIssueRequestConsumer] DLQ 전송 완료: eventId={}", message.eventId());
        } catch (Exception e) {
            throw new RuntimeException(
                    "DLQ 전송 실패 — 전체 배치 재배달 필요: eventId=" + message.eventId(), e);
        }
    }
}
