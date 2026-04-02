package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueException;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponIssueProcessor processor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "coupon-issue-requests",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.error("쿠폰 발급 요청 처리 실패. partition={}, offset={}", record.partition(), record.offset(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record) throws Exception {
        byte[] bytes = (byte[]) record.value();
        Map<String, Object> payload = objectMapper.readValue(bytes, new TypeReference<>() {});

        Long requestId = ((Number) payload.get("requestId")).longValue();
        Long userId = ((Number) payload.get("userId")).longValue();
        Long templateId = ((Number) payload.get("templateId")).longValue();

        try {
            processor.process(requestId, userId, templateId);
        } catch (CouponIssueException e) {
            processor.markFailed(requestId, e.getMessage());
        } catch (Exception e) {
            processor.markFailed(requestId, "발급 처리 중 오류가 발생했습니다.");
            throw e;
        }
    }
}
