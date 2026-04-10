package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestMessage;
import com.loopers.infrastructure.kafka.KafkaConsumerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 선착순 쿠폰 발급 요청 Kafka Consumer.
 * <p>
 * coupon-issue-requests 토픽에서 단건 메시지를 수신하여
 * {@link CouponIssueProcessor}에게 위임한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponIssueConsumer {

    private final CouponIssueProcessor couponIssueProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "coupon-issue-requests",
            groupId = "commerce-streamer-coupon",
            containerFactory = KafkaConsumerConfig.SINGLE_LISTENER
    )
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            CouponIssueRequestMessage message = objectMapper.readValue(
                    record.value(), CouponIssueRequestMessage.class);
            log.info("쿠폰 발급 요청 수신: requestId={}, userId={}, couponId={}",
                    message.requestId(), message.userId(), message.couponId());

            couponIssueProcessor.process(message);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("쿠폰 발급 요청 처리 실패: offset={}", record.offset(), e);
            acknowledgment.acknowledge(); // DLQ로 이동하지 않도록 ACK 처리 (결과 테이블에 기록됨)
        }
    }
}
