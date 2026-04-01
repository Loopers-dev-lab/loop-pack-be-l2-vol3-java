package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.coupon.CouponIssueProcessor.BusinessFailureException;
import com.loopers.infrastructure.dlq.DlqPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 선착순 쿠폰 발급 Consumer — 메시지 수신 + ACK + DLQ만 담당
 *
 * Interfaces 레이어의 책임: "요청 수신"
 *   Controller가 HTTP 요청을 받아서 Facade에 위임하듯이,
 *   Consumer가 Kafka 메시지를 받아서 Processor에 위임한다.
 *
 * 비즈니스 처리는 CouponIssueProcessor(@Service)에 위임:
 *   → 프록시를 통한 호출 → @Transactional 정상 동작
 *   → self-invocation 방지 → 발급 + 상태 업데이트 + event_handled 같은 TX
 *
 * 예외 전략 (건별 격리 — 배치 내 1건 실패가 나머지 건을 중단시키지 않음):
 *   BusinessFailureException → 별도 TX로 FAILED 기록 (재시도 불필요)
 *   그 외 Exception → DLQ로 수동 발행 후 나머지 건 계속 처리
 */
@Component
public class CouponIssueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    private final CouponIssueProcessor processor;
    private final DlqPublisher dlqPublisher;

    public CouponIssueConsumer(CouponIssueProcessor processor, DlqPublisher dlqPublisher) {
        this.processor = processor;
        this.dlqPublisher = dlqPublisher;
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
                // 비즈니스 실패 → process()의 TX는 rollback-only 상태.
                // 별도 TX(REQUIRES_NEW)로 FAILED + event_handled 기록.
                processor.markFailedInNewTx(e.getRequestId(), e.getEventId(), e.getMessage());
                log.warn("[CouponIssue] 비즈니스 실패 — error={}", e.getMessage());

            } catch (Exception e) {
                // 인프라 장애 → DLQ로 수동 발행 후 나머지 건 계속 처리
                log.error("[CouponIssue] 인프라 실패 → DLQ — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
                dlqPublisher.sendToDlq(record, e);
            }
        }
        ack.acknowledge();
    }
}
