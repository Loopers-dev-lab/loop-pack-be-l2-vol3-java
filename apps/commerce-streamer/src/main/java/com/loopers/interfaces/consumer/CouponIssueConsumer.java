package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "coupon-issue-requests",
        groupId = "coupon-issuer",
        containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    public void consume(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        try {
            tx.executeWithoutResult(status -> processRecord(record));
        } catch (Exception e) {
            log.error("CouponIssueConsumer 처리 실패 — offset={}", record.offset(), e);
        }

        ack.acknowledge();
    }

    private void processRecord(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            long requestId = payload.get("requestId").asLong();
            long couponId = payload.get("couponId").asLong();
            long memberId = payload.get("memberId").asLong();

            String eventId = "coupon-issue-" + requestId;

            // INSERT-first 멱등 패턴
            int inserted = entityManager.createNativeQuery(
                "INSERT IGNORE INTO event_handled (event_id, event_type, created_at) VALUES (:eventId, 'COUPON_ISSUE', NOW(6))"
            ).setParameter("eventId", eventId)
             .executeUpdate();

            if (inserted == 0) {
                log.debug("이미 처리된 쿠폰 발급 요청 — requestId={}", requestId);
                return;
            }

            // CAS UPDATE: issued_count 증가 (수량 확인)
            int casResult = entityManager.createNativeQuery(
                "UPDATE coupon SET issued_count = issued_count + 1 "
                    + "WHERE id = :couponId "
                    + "AND (max_issuance_count IS NULL OR issued_count < max_issuance_count) "
                    + "AND deleted_at IS NULL"
            ).setParameter("couponId", couponId)
             .executeUpdate();

            if (casResult == 0) {
                rejectRequest(requestId, "수량 소진");
                return;
            }

            // coupon_issue INSERT (UNIQUE 제약으로 중복 방지)
            try {
                entityManager.createNativeQuery(
                    "INSERT INTO coupon_issue (coupon_id, member_id, status, expired_at, created_at) "
                        + "SELECT :couponId, :memberId, 'AVAILABLE', c.expired_at, NOW(6) "
                        + "FROM coupon c WHERE c.id = :couponId"
                ).setParameter("couponId", couponId)
                 .setParameter("memberId", memberId)
                 .executeUpdate();
            } catch (Exception e) {
                // UNIQUE 제약 위반 → 중복 발급 시도
                entityManager.createNativeQuery(
                    "UPDATE coupon SET issued_count = issued_count - 1 WHERE id = :couponId"
                ).setParameter("couponId", couponId)
                 .executeUpdate();
                rejectRequest(requestId, "이미 발급된 쿠폰");
                return;
            }

            // 성공 상태 업데이트
            entityManager.createNativeQuery(
                "UPDATE coupon_issue_request SET status = 'COMPLETED', completed_at = NOW(6) "
                    + "WHERE id = :requestId"
            ).setParameter("requestId", requestId)
             .executeUpdate();

            log.info("쿠폰 발급 완료 — requestId={}, couponId={}, memberId={}", requestId, couponId, memberId);

        } catch (Exception e) {
            throw new RuntimeException("쿠폰 발급 처리 실패", e);
        }
    }

    private void rejectRequest(long requestId, String reason) {
        entityManager.createNativeQuery(
            "UPDATE coupon_issue_request SET status = 'REJECTED', reject_reason = :reason, completed_at = NOW(6) "
                + "WHERE id = :requestId"
        ).setParameter("requestId", requestId)
         .setParameter("reason", reason)
         .executeUpdate();
        log.info("쿠폰 발급 거절 — requestId={}, reason={}", requestId, reason);
    }
}
