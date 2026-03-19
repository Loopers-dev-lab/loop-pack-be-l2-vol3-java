package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Random;

/**
 * 상품 10만건 시딩
 *
 * 분포 설계:
 * - brand_id: 파레토 — 상위 80개 브랜드(1~80)에 60%, 나머지 320개 ACTIVE(81~400)에 35%, INACTIVE(401~500)에 5%
 * - status: ACTIVE 70% / SOLDOUT 15% / HIDDEN 10% / DISCONTINUED 5%
 * - base_price: 로그 정규분포 (1,300 ~ 5,000,000원)
 * - like_count: 멱법칙 (80%: 0~10, 15%: 11~100, 4%: 101~500, 1%: 500~5000)
 * - deleted_at: 95% NULL / 5% 삭제
 * - created_at: 최근 1년, 최근 3개월에 40% 집중
 */
@Component
class ProductSeeder {

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);
    static final int PRODUCT_COUNT = 200_000;
    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(42); // 재현 가능한 시드

    // 시딩 후 각 상품의 likeCount를 저장 (ProductLikeSeeder에서 사용)
    private int[] likeCountsPerProduct;

    ProductSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    int[] getLikeCountsPerProduct() {
        return likeCountsPerProduct;
    }

    void seed() {
        log.info("[ProductSeeder] {}건 생성 시작", PRODUCT_COUNT);
        long start = System.currentTimeMillis();

        likeCountsPerProduct = new int[PRODUCT_COUNT];

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        // 배치 단위로 나눠서 삽입 (메모리 절약)
        for (int batchStart = 0; batchStart < PRODUCT_COUNT; batchStart += BATCH_SIZE) {
            int currentBatchStart = batchStart;
            int currentBatchSize = Math.min(BATCH_SIZE, PRODUCT_COUNT - batchStart);

            jdbcTemplate.batchUpdate(
                "INSERT INTO products (brand_id, name, description, base_price, status, like_count, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        int globalIdx = currentBatchStart + i;
                        int productNum = globalIdx + 1;

                        // brand_id: 파레토 분포
                        long brandId = generateBrandId();
                        ps.setLong(1, brandId);

                        ps.setString(2, "상품" + productNum);
                        ps.setString(3, "상품" + productNum + " 설명입니다.");

                        // base_price: 로그 정규분포
                        int price = generatePrice();
                        ps.setInt(4, price);

                        // status: ACTIVE 70% / SOLDOUT 15% / HIDDEN 10% / DISCONTINUED 5%
                        String status = generateStatus();
                        ps.setString(5, status);

                        // like_count: 멱법칙
                        int likeCount = generateLikeCount();
                        likeCountsPerProduct[globalIdx] = likeCount;
                        ps.setInt(6, likeCount);

                        // created_at: 최근 1년, 최근 3개월 40% 집중
                        ZonedDateTime createdAt = generateCreatedAt(now);
                        ps.setTimestamp(7, Timestamp.from(createdAt.toInstant()));
                        ps.setTimestamp(8, Timestamp.from(now.toInstant()));

                        // deleted_at: 5% 삭제
                        if (random.nextDouble() < 0.05) {
                            ps.setTimestamp(9, Timestamp.from(now.minusDays(random.nextInt(30)).toInstant()));
                        } else {
                            ps.setNull(9, java.sql.Types.TIMESTAMP);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return currentBatchSize;
                    }
                }
            );

            if ((currentBatchStart + currentBatchSize) % 20_000 == 0) {
                log.info("[ProductSeeder] {}/{} 완료", currentBatchStart + currentBatchSize, PRODUCT_COUNT);
            }
        }

        log.info("[ProductSeeder] {}건 생성 완료 ({}ms)", PRODUCT_COUNT, System.currentTimeMillis() - start);
    }

    /**
     * 파레토 분포: 상위 80개(1~80) 60%, 중위 320개(81~400) 35%, INACTIVE(401~500) 5%
     */
    private long generateBrandId() {
        double r = random.nextDouble();
        if (r < 0.60) {
            return random.nextInt(80) + 1;       // 1~80 (대형 브랜드)
        } else if (r < 0.95) {
            return random.nextInt(320) + 81;     // 81~400 (중소 브랜드)
        } else {
            return random.nextInt(100) + 401;    // 401~500 (INACTIVE 브랜드)
        }
    }

    /**
     * 로그 정규분포: 1,300 ~ 5,000,000원, 중심은 1만~10만원
     */
    private int generatePrice() {
        // log-normal: mean=10 (≈22,000원), sigma=1.0
        double logPrice = 10.0 + random.nextGaussian() * 1.0;
        int price = (int) Math.exp(logPrice);
        price = Math.max(1_300, Math.min(5_000_000, price));
        // 100원 단위로 반올림
        return (price / 100) * 100;
    }

    private String generateStatus() {
        double r = random.nextDouble();
        if (r < 0.70) return "ACTIVE";
        if (r < 0.85) return "SOLDOUT";
        if (r < 0.95) return "HIDDEN";
        return "DISCONTINUED";
    }

    /**
     * 멱법칙: 80%: 0~10, 15%: 11~100, 4%: 101~500, 1%: 500~5000
     */
    private int generateLikeCount() {
        double r = random.nextDouble();
        if (r < 0.80) return random.nextInt(11);          // 0~10
        if (r < 0.95) return random.nextInt(90) + 11;     // 11~100
        if (r < 0.99) return random.nextInt(400) + 101;   // 101~500
        return random.nextInt(4500) + 501;                 // 501~5000
    }

    /**
     * 최근 1년, 최근 3개월에 40% 집중
     */
    private ZonedDateTime generateCreatedAt(ZonedDateTime now) {
        double r = random.nextDouble();
        if (r < 0.40) {
            // 최근 3개월 (0~90일 전)
            return now.minusDays(random.nextInt(90)).minusHours(random.nextInt(24));
        } else {
            // 3개월~1년 전 (91~365일 전)
            return now.minusDays(random.nextInt(275) + 91).minusHours(random.nextInt(24));
        }
    }
}
