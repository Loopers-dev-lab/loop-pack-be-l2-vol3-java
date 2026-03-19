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
 * product_likes 시딩
 *
 * products.like_count와 정합성을 맞춤:
 * 각 상품의 like_count만큼 실제 product_likes 레코드를 생성한다.
 * UniqueConstraint(user_id, product_id)를 위반하지 않도록 유저를 중복 없이 선택한다.
 */
@Component
class ProductLikeSeeder {

    private static final Logger log = LoggerFactory.getLogger(ProductLikeSeeder.class);
    private static final int BATCH_SIZE = 5_000;
    private static final int USER_COUNT = UserSeeder.USER_COUNT;

    private final JdbcTemplate jdbcTemplate;
    private final ProductSeeder productSeeder;
    private final Random random = new Random(42);

    ProductLikeSeeder(JdbcTemplate jdbcTemplate, ProductSeeder productSeeder) {
        this.jdbcTemplate = jdbcTemplate;
        this.productSeeder = productSeeder;
    }

    void seed() {
        int[] likeCountsPerProduct = productSeeder.getLikeCountsPerProduct();
        if (likeCountsPerProduct == null) {
            log.warn("[ProductLikeSeeder] ProductSeeder 미실행. 건너뜁니다.");
            return;
        }

        // 전체 좋아요 건수 계산
        long totalLikes = 0;
        for (int lc : likeCountsPerProduct) {
            totalLikes += Math.min(lc, USER_COUNT); // 유저 수 초과 불가
        }
        log.info("[ProductLikeSeeder] 약 {}건 생성 시작", totalLikes);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        for (int productIdx = 0; productIdx < likeCountsPerProduct.length; productIdx++) {
            int likeCount = Math.min(likeCountsPerProduct[productIdx], USER_COUNT);
            if (likeCount == 0) continue;

            long productId = productIdx + 1;
            Set<Integer> usedUsers = new HashSet<>();

            for (int j = 0; j < likeCount; j++) {
                // 중복 없이 유저 선택
                int userId;
                do {
                    userId = random.nextInt(USER_COUNT) + 1;
                } while (usedUsers.contains(userId));
                usedUsers.add(userId);

                batch.add(new Object[]{
                    userId,
                    productId,
                    Timestamp.from(now.minusDays(random.nextInt(180)).toInstant())
                });

                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(batch);
                    inserted += batch.size();
                    batch.clear();

                    if (inserted % 50_000 == 0) {
                        log.info("[ProductLikeSeeder] {}/{} 완료", inserted, totalLikes);
                    }
                }
            }
        }

        // 남은 배치 처리
        if (!batch.isEmpty()) {
            flushBatch(batch);
            inserted += batch.size();
        }

        log.info("[ProductLikeSeeder] {}건 생성 완료 ({}ms)", inserted, System.currentTimeMillis() - start);
    }

    private void flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO product_likes (user_id, product_id, created_at) VALUES (?, ?, ?)",
            batch
        );
    }
}
