package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * issued_coupons 시딩 (~30,000건)
 *
 * 분포:
 * - user_id: 상위 20% 유저가 50% 보유
 * - status: USED 50% / UNUSED 25% / EXPIRED 20% / CANCELED 5%
 * - USED인 경우 order_id 매핑
 */
@Component
class IssuedCouponSeeder {

    private static final Logger log = LoggerFactory.getLogger(IssuedCouponSeeder.class);
    private static final int ISSUED_COUNT = 30_000;
    private static final int BATCH_SIZE = 5_000;
    private static final int USER_COUNT = UserSeeder.USER_COUNT;
    private static final int TEMPLATE_COUNT = CouponTemplateSeeder.TEMPLATE_COUNT;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(66);

    IssuedCouponSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[IssuedCouponSeeder] {}건 생성 시작", ISSUED_COUNT);
        long start = System.currentTimeMillis();

        // PAID 주문 ID 목록 조회 (USED 쿠폰에 매핑용)
        List<Long> paidOrderIds = jdbcTemplate.queryForList(
            "SELECT id FROM orders WHERE status = 'PAID' ORDER BY id", Long.class
        );

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;
        int paidOrderIdx = 0;

        for (int i = 0; i < ISSUED_COUNT; i++) {
            long userId = generateUserId();
            long templateId = generateTemplateId();
            String status = generateStatus();

            ZonedDateTime createdAt = now.minusDays(random.nextInt(365));
            ZonedDateTime usedAt = null;
            Long orderId = null;

            if ("USED".equals(status) && paidOrderIdx < paidOrderIds.size()) {
                orderId = paidOrderIds.get(paidOrderIdx % paidOrderIds.size());
                paidOrderIdx++;
                usedAt = createdAt.plusDays(random.nextInt(30) + 1);
            }

            // 쿠폰 스냅샷 필드
            boolean isPercentage = random.nextDouble() < 0.60;
            String discountType = isPercentage ? "PERCENT" : "FIXED";
            int discountValue = isPercentage ? (random.nextInt(6) + 1) * 5 : (random.nextInt(10) + 1) * 1_000;
            Integer maxDiscountAmount = isPercentage ? (random.nextInt(10) + 1) * 5_000 : null;

            batch.add(new Object[]{
                templateId, userId, status, orderId,
                usedAt != null ? Timestamp.from(usedAt.toInstant()) : null,
                "쿠폰" + templateId, discountType, discountValue, maxDiscountAmount,
                Timestamp.from(createdAt.toInstant()),
                Timestamp.from(now.toInstant()),
                null // deleted_at
            });

            if (batch.size() >= BATCH_SIZE) {
                flushBatch(batch);
                inserted += batch.size();
                batch.clear();

                if (inserted % 10_000 == 0) {
                    log.info("[IssuedCouponSeeder] {}/{} 완료", inserted, ISSUED_COUNT);
                }
            }
        }

        if (!batch.isEmpty()) {
            flushBatch(batch);
            inserted += batch.size();
        }

        log.info("[IssuedCouponSeeder] {}건 생성 완료 ({}ms)", inserted, System.currentTimeMillis() - start);
    }

    /** 상위 20% 유저가 50% 보유 */
    private long generateUserId() {
        if (random.nextDouble() < 0.50) {
            return random.nextInt(USER_COUNT / 5) + 1;
        }
        return random.nextInt(USER_COUNT * 4 / 5) + USER_COUNT / 5 + 1;
    }

    /** ACTIVE 쿠폰(1~30)에 70% 집중 */
    private long generateTemplateId() {
        if (random.nextDouble() < 0.70) {
            return random.nextInt(30) + 1;
        }
        return random.nextInt(20) + 31;
    }

    /** USED 50% / ISSUED 30% / EXPIRED 20% */
    private String generateStatus() {
        double r = random.nextDouble();
        if (r < 0.50) return "USED";
        if (r < 0.80) return "ISSUED";
        return "EXPIRED";
    }

    private void flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO issued_coupons (coupon_template_id, user_id, status, order_id, " +
            "used_at, coupon_name, discount_type, discount_value, max_discount_amount, " +
            "created_at, updated_at, deleted_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            batch
        );
    }
}
