package com.loopers.interfaces.consumer;

import com.loopers.application.CouponIssueProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.CouponIssueResultService;
import com.loopers.domain.coupon.event.CouponIssueMessage;
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

    private final CouponIssueProcessor couponIssueProcessor;
    private final CouponIssueResultService couponIssueResultService;

    @KafkaListener(
            topics = "coupon-issue-requests",
            containerFactory = KafkaConfig.BATCH_LISTENER,
            groupId = "coupon-issue-consumer"
    )
    public void consume(
            List<ConsumerRecord<String, CouponIssueMessage>> records,
            Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<String, CouponIssueMessage> record : records) {
            CouponIssueMessage message = record.value();
            log.info("쿠폰 발급 요청 수신: requestId={}, couponId={}, memberId={}",
                    message.requestId(), message.couponId(), message.memberId());

            try {
                couponIssueProcessor.process(message);
            } catch (Exception e) {
                log.error("쿠폰 발급 처리 실패: {}", message, e);
                try {
                    couponIssueResultService.markFailed(
                            message.requestId(), "처리 중 오류: " + e.getMessage());
                } catch (Exception markEx) {
                    log.error("발급 실패 상태 업데이트도 실패: requestId={}", message.requestId(), markEx);
                }
            }
        }

        acknowledgment.acknowledge();
    }
}
