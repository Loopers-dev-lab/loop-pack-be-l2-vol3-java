package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueAppService;
import com.loopers.application.coupon.CouponIssueMessage;
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
    private final CouponIssueAppService couponIssueAppService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "coupon-issue-requests",
            groupId = "coupon-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> messages, Acknowledgment acknowledgment) {
        boolean hasFailure = false;
        for (ConsumerRecord<String, String> record : messages) {
            try {
                CouponIssueMessage message = objectMapper.readValue(record.value(), CouponIssueMessage.class);
                couponIssueAppService.processCouponIssue(
                        message.requestId(), message.couponId(), message.userId());
            } catch (JsonProcessingException e) {
                log.error("coupon-issue-requests 메시지 파싱 실패 (skip): {}", record.value(), e);
            } catch (Exception e) {
                log.error("coupon-issue-requests 처리 실패: {}", record.value(), e);
                hasFailure = true;
            }
        }
        if (!hasFailure) {
            acknowledgment.acknowledge();
        }
    }
}
