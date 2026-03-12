package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * orders + order_items 시딩 (케브 멘토 추가 숙제용)
 *
 * 분포:
 * - user_id: 불균등 (상위 10% 유저가 50% 주문)
 * - status: PAID 70% / CANCELED 15% / PENDING 10% / EXPIRED 5%
 * - ordered_at: 최근 6개월 분산
 * - items per order: 1개 60% / 2개 30% / 3개 10%
 */
@Component
class OrderSeeder {

    private static final Logger log = LoggerFactory.getLogger(OrderSeeder.class);
    private static final int ORDER_COUNT = 100_000;
    private static final int BATCH_SIZE = 2_000;
    private static final int USER_COUNT = UserSeeder.USER_COUNT;
    private static final int PRODUCT_COUNT = ProductSeeder.PRODUCT_COUNT;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(42);

    OrderSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[OrderSeeder] 주문 {}건 + 주문상품 생성 시작", ORDER_COUNT);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        List<Object[]> orderBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> itemBatch = new ArrayList<>(BATCH_SIZE * 2);
        long totalItems = 0;

        for (int i = 0; i < ORDER_COUNT; i++) {
            long userId = generateUserId();
            String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
            String status = generateOrderStatus();
            ZonedDateTime orderedAt = now.minusDays(random.nextInt(180)).minusHours(random.nextInt(24));

            int itemCount = generateItemCount();
            int subtotal = 0;

            // 주문 상품 준비
            List<Object[]> currentItems = new ArrayList<>();
            for (int j = 0; j < itemCount; j++) {
                long productId = random.nextInt(PRODUCT_COUNT) + 1;
                int unitPrice = (random.nextInt(490) + 10) * 1000; // 10,000 ~ 500,000
                int quantity = random.nextInt(3) + 1; // 1~3
                subtotal += unitPrice * quantity;

                currentItems.add(new Object[]{
                    productId,
                    "상품" + productId,
                    "브랜드" + (random.nextInt(100) + 1),
                    unitPrice,
                    quantity
                });
            }

            int discountAmount = random.nextDouble() < 0.3 ? random.nextInt(5000) + 1000 : 0;
            int pointUsed = random.nextDouble() < 0.2 ? random.nextInt(3000) : 0;
            int shippingFee = subtotal >= 50_000 ? 0 : 3_000;
            int totalAmount = Math.max(0, subtotal - discountAmount - pointUsed + shippingFee);

            ZonedDateTime canceledAt = "CANCELED".equals(status) ? orderedAt.plusDays(1) : null;
            ZonedDateTime expiresAt = "PENDING".equals(status) ? orderedAt.plusMinutes(30) : null;

            orderBatch.add(new Object[]{
                userId, orderNumber, subtotal, discountAmount, pointUsed, shippingFee, totalAmount,
                status,
                "주문자" + userId, "010-1234-" + String.format("%04d", userId),
                "수령인" + userId, "010-5678-" + String.format("%04d", userId),
                "12345", "서울시 강남구 테스트로 " + userId, "테스트동 " + (i + 1) + "호",
                null, null, random.nextDouble() < 0.5 ? "CARD" : "BANK_TRANSFER",
                Timestamp.from(orderedAt.toInstant()),
                expiresAt != null ? Timestamp.from(expiresAt.toInstant()) : null,
                canceledAt != null ? Timestamp.from(canceledAt.toInstant()) : null,
                Timestamp.from(orderedAt.toInstant()),
                Timestamp.from(now.toInstant())
            });

            // order_items는 order INSERT 후 order_id를 알아야 하므로 별도 저장
            for (Object[] item : currentItems) {
                itemBatch.add(item);
            }
            totalItems += currentItems.size();

            if (orderBatch.size() >= BATCH_SIZE) {
                flushOrders(orderBatch);
                orderBatch.clear();

                if ((i + 1) % 10_000 == 0) {
                    log.info("[OrderSeeder] 주문 {}/{} 완료", i + 1, ORDER_COUNT);
                }
            }
        }

        // 남은 주문 배치
        if (!orderBatch.isEmpty()) {
            flushOrders(orderBatch);
        }

        // order_items: order_id를 DB에서 매핑
        seedOrderItems(totalItems);

        log.info("[OrderSeeder] 주문 {}건 + 주문상품 {}건 생성 완료 ({}ms)",
            ORDER_COUNT, totalItems, System.currentTimeMillis() - start);
    }

    /**
     * order_items 시딩: orders 삽입 후, 각 order의 item_count에 맞게 생성
     */
    private void seedOrderItems(long expectedTotal) {
        log.info("[OrderSeeder] 주문상품 {}건 생성 시작", expectedTotal);

        // 주문 ID 목록 조회
        List<Long> orderIds = jdbcTemplate.queryForList("SELECT id FROM orders ORDER BY id", Long.class);

        List<Object[]> batch = new ArrayList<>(5_000);
        long inserted = 0;
        Random itemRandom = new Random(123);

        for (Long orderId : orderIds) {
            int itemCount = generateItemCountWith(itemRandom);

            for (int j = 0; j < itemCount; j++) {
                long productId = itemRandom.nextInt(PRODUCT_COUNT) + 1;
                int unitPrice = (itemRandom.nextInt(490) + 10) * 1000;
                int quantity = itemRandom.nextInt(3) + 1;

                batch.add(new Object[]{
                    orderId,
                    productId,
                    "상품" + productId,
                    "브랜드" + (itemRandom.nextInt(100) + 1),
                    unitPrice,
                    quantity
                });

                if (batch.size() >= 5_000) {
                    flushOrderItems(batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            flushOrderItems(batch);
            inserted += batch.size();
        }

        log.info("[OrderSeeder] 주문상품 {}건 생성 완료", inserted);
    }

    private void flushOrders(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO orders (user_id, order_number, subtotal_amount, discount_amount, point_used_amount, " +
            "shipping_fee, total_amount, status, orderer_name, orderer_phone, receiver_name, receiver_phone, " +
            "zip_code, address_line1, address_line2, coupon_id, payment_id, payment_method, " +
            "ordered_at, expires_at, canceled_at, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            batch
        );
    }

    private void flushOrderItems(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO order_items (order_id, product_id, product_name, brand_name, unit_price, quantity) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            batch
        );
    }

    /** 상위 10%(1~500)가 50% 주문 */
    private long generateUserId() {
        if (random.nextDouble() < 0.50) {
            return random.nextInt(500) + 1;     // 헤비 유저 (1~500)
        }
        return random.nextInt(4500) + 501;      // 일반 유저 (501~5000)
    }

    private String generateOrderStatus() {
        double r = random.nextDouble();
        if (r < 0.70) return "PAID";
        if (r < 0.85) return "CANCELED";
        if (r < 0.95) return "PENDING";
        return "EXPIRED";
    }

    /** 1개: 60%, 2개: 30%, 3개: 10% */
    private int generateItemCount() {
        double r = random.nextDouble();
        if (r < 0.60) return 1;
        if (r < 0.90) return 2;
        return 3;
    }

    private int generateItemCountWith(Random r) {
        double v = r.nextDouble();
        if (v < 0.60) return 1;
        if (v < 0.90) return 2;
        return 3;
    }
}
