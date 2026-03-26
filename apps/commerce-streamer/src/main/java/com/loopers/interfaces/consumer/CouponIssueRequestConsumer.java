package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.interfaces.consumer.payload.CouponIssuePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueRequestConsumer {

    private final CouponIssueFacade couponIssueFacade;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "coupon-issue-requests", containerFactory = KafkaConfig.SINGLE_LISTENER)
    public void consume(ConsumerRecord<Object, Object> record, Acknowledgment ack) {
        try {
            CouponIssuePayload payload = objectMapper.readValue((String) record.value(), CouponIssuePayload.class);
            couponIssueFacade.processIssue(payload);
        } catch (Exception e) {
            log.error("coupon-issue-requests 처리 실패, skip. offset={}", record.offset(), e);
        }
        ack.acknowledge();
    }
}
