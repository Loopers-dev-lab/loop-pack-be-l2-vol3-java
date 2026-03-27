package com.loopers.interfaces.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.loopers.application.coupon.CouponIssueService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.CouponIssueStatusManager;
import com.loopers.interfaces.consumer.dto.CouponIssueMessageDto.CouponIssueMessage;
import com.loopers.interfaces.consumer.support.KafkaMessageParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 쿠폰 발급 커맨드를 소비하는 Kafka Consumer.
 *
 * <p>{@code coupon-issue-v1} 토픽에서 발급 커맨드를 배치로 수신하여
 * {@link CouponIssueService}에 위임한다.
 * 개별 메시지 처리 실패 시 로그를 남기고 나머지 메시지는 계속 처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private static final String TOPIC = "coupon-issue-v1";

    private final CouponIssueService couponIssueService;
    private final CouponIssueStatusManager couponIssueStatusManager;
    private final KafkaMessageParser kafkaMessageParser;

    @KafkaListener(
            topics = TOPIC,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        log.debug("[CouponIssue] 배치 수신: size={}", messages.size());
        for (ConsumerRecord<String, Object> record : messages) {
            CouponIssueMessage msg = null;
            try {
                msg = kafkaMessageParser.parse(record.value(), CouponIssueMessage.class);
                String eventId = "coupon-issue:" + msg.couponId() + ":" + msg.userId();

                couponIssueService.issue(eventId, msg.couponId(), msg.userId());
                couponIssueStatusManager.markCompleted(msg.couponId(), msg.userId());
            } catch (Exception e) {
                log.error("[CouponIssue] 처리 실패: topic={}, offset={}, partition={}",
                        record.topic(), record.offset(), record.partition(), e);
                markFailedSafely(msg);
            }
        }
        ack.acknowledge();
    }

    private void markFailedSafely(CouponIssueMessage msg) {
        if (msg == null) {
            return;
        }
        try {
            couponIssueStatusManager.markFailed(msg.couponId(), msg.userId());
        } catch (Exception e) {
            log.warn("[CouponIssue] 상태 업데이트 실패: couponId={}, userId={}", msg.couponId(), msg.userId(), e);
        }
    }
}
