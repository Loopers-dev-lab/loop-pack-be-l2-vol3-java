package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.coupon.CouponIssueProcessor.BusinessFailureException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 선착순 쿠폰 발급 Consumer — 메시지 수신 + ACK만 담당
 *
 * Interfaces 레이어의 책임: "요청 수신"
 *   Controller가 HTTP 요청을 받아서 Facade에 위임하듯이,
 *   Consumer가 Kafka 메시지를 받아서 Processor에 위임한다.
 *
 * 비즈니스 처리는 CouponIssueProcessor(@Service)에 위임:
 *   → 프록시를 통한 호출 → @Transactional 정상 동작
 *   → self-invocation 방지 → 발급 + 상태 업데이트 + event_handled 같은 TX
 *
 * 예외 전략:
 *   BusinessFailureException → 재시도 불필요 (이미 FAILED 기록됨)
 *   그 외 Exception → Spring ErrorHandler가 DLQ로 즉시 격리
 */
@Component
public class CouponIssueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    private final CouponIssueProcessor processor;

    public CouponIssueConsumer(CouponIssueProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(
            topics = "coupon-issue-requests-v1",
            groupId = "coupon-issue-group",
            containerFactory = "BATCH_LISTENER_DEFAULT"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                String payload = record.value().toString();
                processor.process(payload);

            } catch (BusinessFailureException e) {
                // 비즈니스 실패 → 재시도 불필요 (재고 소진, 중복 발급 등)
                // Processor에서 이미 FAILED 기록 + 멱등성 기록 완료
                log.warn("[CouponIssue] 비즈니스 실패 — error={}", e.getMessage());

            } catch (Exception e) {
                // 인프라 장애 → Spring ErrorHandler가 DLQ로 즉시 격리
                log.error("[CouponIssue] 인프라 실패 → ErrorHandler 위임 — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage());
                throw e;
            }
        }
        ack.acknowledge();
    }
}
