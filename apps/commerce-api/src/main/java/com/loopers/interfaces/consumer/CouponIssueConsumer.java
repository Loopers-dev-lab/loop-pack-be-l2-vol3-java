package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.CouponIssueMessage;
import com.loopers.domain.outbox.OutboxEventTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final CouponIssueRequestProcessor processor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = OutboxEventTopics.COUPON_ISSUE,
        groupId = "coupon-issue-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                byte[] valueBytes = (byte[]) record.value();
                CouponIssueMessage message = objectMapper.readValue(valueBytes, CouponIssueMessage.class);
                processor.process(message);
            } catch (Exception e) {
                log.warn("쿠폰 발급 메시지 처리 실패. topic={}, offset={}, 이유={}",
                    record.topic(), record.offset(), e.getMessage());
            }
        }
        acknowledgment.acknowledge();
    }
}