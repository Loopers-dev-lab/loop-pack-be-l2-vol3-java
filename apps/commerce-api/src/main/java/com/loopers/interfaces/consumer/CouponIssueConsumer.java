package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final CouponIssueProcessor couponIssueProcessor;

    @KafkaListener(
        topics = "coupon.issue-requests.topic-v1",
        groupId = "coupon-issue-processor",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                JsonNode node = objectMapper.readTree(record.value());
                Long requestId = node.get("requestId").asLong();
                couponIssueProcessor.process(requestId);
            } catch (Exception e) {
                log.warn("쿠폰 발급 요청 처리 실패 - offset: {}, partition: {}",
                    record.offset(), record.partition(), e);
            }
        }
        acknowledgment.acknowledge();
    }
}
