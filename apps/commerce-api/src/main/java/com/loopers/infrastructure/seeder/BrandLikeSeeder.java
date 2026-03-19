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
 * brand_likes 시딩 (~50,000건)
 *
 * 분포:
 * - 인기 브랜드(상위 80개)에 좋아요 집중 (멱법칙)
 * - 유저당 평균 10개 브랜드, 1~50개 범위
 * - UniqueConstraint(user_id, brand_id) 준수
 */
@Component
class BrandLikeSeeder {

    private static final Logger log = LoggerFactory.getLogger(BrandLikeSeeder.class);
    private static final int BATCH_SIZE = 5_000;
    private static final int USER_COUNT = UserSeeder.USER_COUNT;
    private static final int BRAND_COUNT = BrandSeeder.BRAND_COUNT;
    private static final int ACTIVE_BRAND_COUNT = BrandSeeder.ACTIVE_BRAND_COUNT;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(77);

    BrandLikeSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[BrandLikeSeeder] 브랜드 좋아요 생성 시작");
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        // 유저별로 좋아요할 브랜드 수 결정
        for (int userId = 1; userId <= USER_COUNT; userId++) {
            int likeCount = generateUserBrandLikeCount();
            Set<Integer> likedBrands = new HashSet<>();

            for (int j = 0; j < likeCount; j++) {
                int brandId;
                do {
                    brandId = generateBrandId();
                } while (likedBrands.contains(brandId));
                likedBrands.add(brandId);

                batch.add(new Object[]{
                    userId,
                    brandId,
                    Timestamp.from(now.minusDays(random.nextInt(180)).toInstant())
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

        log.info("[BrandLikeSeeder] {}건 생성 완료 ({}ms)", inserted, System.currentTimeMillis() - start);
    }

    /** 유저당 브랜드 좋아요 수: 평균 10개, 1~50개 범위 */
    private int generateUserBrandLikeCount() {
        double r = random.nextDouble();
        if (r < 0.30) return random.nextInt(3) + 1;       // 1~3
        if (r < 0.70) return random.nextInt(8) + 4;       // 4~11
        if (r < 0.90) return random.nextInt(15) + 12;     // 12~26
        return random.nextInt(24) + 27;                    // 27~50
    }

    /** 인기 브랜드(1~80)에 집중 */
    private int generateBrandId() {
        double r = random.nextDouble();
        if (r < 0.70) {
            return random.nextInt(80) + 1;           // 대형 브랜드
        } else if (r < 0.95) {
            return random.nextInt(320) + 81;         // 중소 브랜드
        } else {
            return random.nextInt(100) + 401;        // INACTIVE 브랜드
        }
    }

    private void flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO brand_likes (user_id, brand_id, created_at) VALUES (?, ?, ?)",
            batch
        );
    }
}
