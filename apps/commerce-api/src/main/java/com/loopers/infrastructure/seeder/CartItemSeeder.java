package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * cart_items 시딩 (~15,000건)
 *
 * 분포:
 * - 전체 유저의 60%(3,000명)가 장바구니 보유
 * - user당 1~8개, 평균 5개
 * - quantity: 1개 70% / 2개 20% / 3~5개 10%
 * - deleted_at: 90% NULL / 10% 삭제
 * - UniqueConstraint(user_id, product_id) 준수
 */
@Component
class CartItemSeeder {

    private static final Logger log = LoggerFactory.getLogger(CartItemSeeder.class);
    private static final int BATCH_SIZE = 5_000;
    private static final int USER_COUNT = UserSeeder.USER_COUNT;
    private static final int PRODUCT_COUNT = ProductSeeder.PRODUCT_COUNT;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(33);

    CartItemSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[CartItemSeeder] 장바구니 데이터 생성 시작");
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        // 60%의 유저가 장바구니 보유
        for (int userId = 1; userId <= USER_COUNT; userId++) {
            if (random.nextDouble() >= 0.60) continue; // 40%는 장바구니 없음

            int itemCount = generateItemCount();
            Set<Integer> usedProducts = new HashSet<>();

            for (int j = 0; j < itemCount; j++) {
                int productId;
                do {
                    productId = generateProductId();
                } while (usedProducts.contains(productId));
                usedProducts.add(productId);

                int quantity = generateQuantity();
                ZonedDateTime createdAt = now.minusDays(random.nextInt(30));
                boolean isDeleted = random.nextDouble() < 0.10;

                batch.add(new Object[]{
                    userId, productId, quantity,
                    Timestamp.from(createdAt.toInstant()),
                    Timestamp.from(now.toInstant()),
                    isDeleted ? Timestamp.from(now.minusDays(random.nextInt(7)).toInstant()) : null
                });

                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            flushBatch(batch);
            inserted += batch.size();
        }

        log.info("[CartItemSeeder] {}건 생성 완료 ({}ms)", inserted, System.currentTimeMillis() - start);
    }

    /** 1~8개, 평균 5개 */
    private int generateItemCount() {
        double r = random.nextDouble();
        if (r < 0.10) return 1;
        if (r < 0.25) return 2;
        if (r < 0.40) return 3;
        if (r < 0.55) return 4;
        if (r < 0.70) return 5;
        if (r < 0.82) return 6;
        if (r < 0.92) return 7;
        return 8;
    }

    /** 인기 상품에 약간 집중 (1~PRODUCT_COUNT) */
    private int generateProductId() {
        // 상품 ID가 낮을수록 인기 브랜드 상품일 확률 높음
        if (random.nextDouble() < 0.30) {
            return random.nextInt(PRODUCT_COUNT / 10) + 1; // 상위 10%
        }
        return random.nextInt(PRODUCT_COUNT) + 1;
    }

    /** 1개 70% / 2개 20% / 3~5개 10% */
    private int generateQuantity() {
        double r = random.nextDouble();
        if (r < 0.70) return 1;
        if (r < 0.90) return 2;
        return random.nextInt(3) + 3; // 3~5
    }

    private void flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO cart_items (user_id, product_id, quantity, created_at, updated_at, deleted_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            batch
        );
    }
}
