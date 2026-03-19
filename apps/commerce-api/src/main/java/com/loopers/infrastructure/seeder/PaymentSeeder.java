package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * payments 시딩 — orders와 1:1 매핑 (PAID/CANCELED 주문 대상)
 *
 * 분포:
 * - status: orders.status와 정합 (PAID→APPROVED, CANCELED→CANCELED, PENDING→PENDING)
 * - payment_method: CREDIT_CARD 50% / BANK_TRANSFER 20% / KAKAO_PAY 20% / NAVER_PAY 10%
 */
@Component
class PaymentSeeder {

    private static final Logger log = LoggerFactory.getLogger(PaymentSeeder.class);
    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(88);

    PaymentSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[PaymentSeeder] 결제 데이터 생성 시작");
        long start = System.currentTimeMillis();

        // 주문 정보 조회
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
            "SELECT id, total_amount, status, ordered_at, canceled_at FROM orders ORDER BY id"
        );

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        for (Map<String, Object> order : orders) {
            Long orderId = ((Number) order.get("id")).longValue();
            int totalAmount = ((Number) order.get("total_amount")).intValue();
            String orderStatus = (String) order.get("status");
            Timestamp orderedAt = toTimestamp(order.get("ordered_at"));
            Timestamp canceledAt = toTimestamp(order.get("canceled_at"));

            // PENDING/EXPIRED 주문도 결제 시도 기록은 있을 수 있음
            String paymentStatus = mapPaymentStatus(orderStatus);
            String paymentMethod = generatePaymentMethod();
            String idempotencyKey = UUID.randomUUID().toString();

            Integer approvedAmount = null;
            String pgTxnId = null;
            Timestamp approvedAtTs = null;
            Timestamp failedAtTs = null;
            Timestamp canceledAtTs = null;

            if ("APPROVED".equals(paymentStatus)) {
                approvedAmount = totalAmount;
                pgTxnId = "PG-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
                approvedAtTs = new Timestamp(orderedAt.getTime() + 1000); // 주문 1초 후 승인
            } else if ("CANCELED".equals(paymentStatus)) {
                approvedAmount = totalAmount;
                pgTxnId = "PG-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
                approvedAtTs = new Timestamp(orderedAt.getTime() + 1000);
                canceledAtTs = canceledAt;
            } else if ("FAILED".equals(paymentStatus)) {
                failedAtTs = new Timestamp(orderedAt.getTime() + 500);
            }
            // PENDING: 모두 null

            batch.add(new Object[]{
                orderId, paymentStatus, paymentMethod, totalAmount,
                approvedAmount, pgTxnId, idempotencyKey,
                orderedAt, approvedAtTs, failedAtTs, canceledAtTs,
                orderedAt, // created_at
                Timestamp.from(now.toInstant()), // updated_at
                null // deleted_at
            });

            if (batch.size() >= BATCH_SIZE) {
                flushBatch(batch);
                inserted += batch.size();
                batch.clear();

                if (inserted % 20_000 == 0) {
                    log.info("[PaymentSeeder] {}/{} 완료", inserted, orders.size());
                }
            }
        }

        if (!batch.isEmpty()) {
            flushBatch(batch);
            inserted += batch.size();
        }

        log.info("[PaymentSeeder] {}건 생성 완료 ({}ms)", inserted, System.currentTimeMillis() - start);
    }

    private String mapPaymentStatus(String orderStatus) {
        return switch (orderStatus) {
            case "PAID" -> "APPROVED";
            case "CANCELED" -> "CANCELED";
            case "PENDING" -> "REQUESTED";
            case "EXPIRED" -> "FAILED";
            default -> "REQUESTED";
        };
    }

    /** CREDIT_CARD 50% / BANK_TRANSFER 20% / KAKAO_PAY 20% / NAVER_PAY 10% */
    private String generatePaymentMethod() {
        double r = random.nextDouble();
        if (r < 0.50) return "CREDIT_CARD";
        if (r < 0.70) return "BANK_TRANSFER";
        if (r < 0.90) return "KAKAO_PAY";
        return "NAVER_PAY";
    }

    /** DB에서 반환된 datetime 값을 Timestamp로 안전하게 변환 */
    private Timestamp toTimestamp(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts;
        if (value instanceof LocalDateTime ldt) return Timestamp.valueOf(ldt);
        throw new IllegalArgumentException("Unsupported datetime type: " + value.getClass());
    }

    private void flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO payments (order_id, status, payment_method, requested_amount, " +
            "approved_amount, pg_txn_id, idempotency_key, " +
            "requested_at, approved_at, failed_at, canceled_at, " +
            "created_at, updated_at, deleted_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            batch
        );
    }
}
