package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Random;

/**
 * coupon_templates 시딩 (50건)
 *
 * 분포:
 * - discount_type: PERCENTAGE 60% / FIXED_AMOUNT 40%
 * - status: ACTIVE 60% / EXPIRED 30% / INACTIVE 10%
 * - valid 기간: 7~90일
 */
@Component
class CouponTemplateSeeder {

    private static final Logger log = LoggerFactory.getLogger(CouponTemplateSeeder.class);
    static final int TEMPLATE_COUNT = 50;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(55);

    CouponTemplateSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[CouponTemplateSeeder] {}건 생성 시작", TEMPLATE_COUNT);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.batchUpdate(
            "INSERT INTO coupon_templates (name, description, discount_type, discount_value, " +
            "max_discount_amount, min_order_amount, max_issue_count, max_issue_count_per_user, " +
            "issued_count, valid_from, valid_to, status, created_at, updated_at, deleted_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = i + 1;
                    boolean isPercentage = random.nextDouble() < 0.60;
                    String discountType = isPercentage ? "PERCENT" : "FIXED";

                    ps.setString(1, "쿠폰" + idx);
                    ps.setString(2, "쿠폰" + idx + " 설명");
                    ps.setString(3, discountType);

                    if (isPercentage) {
                        ps.setInt(4, (random.nextInt(6) + 1) * 5); // 5, 10, 15, 20, 25, 30%
                        ps.setInt(5, (random.nextInt(10) + 1) * 5_000); // 5,000~50,000
                    } else {
                        ps.setInt(4, (random.nextInt(10) + 1) * 1_000); // 1,000~10,000
                        ps.setNull(5, Types.INTEGER);
                    }

                    ps.setInt(6, (random.nextInt(10) + 1) * 10_000); // 10,000~100,000
                    int maxIssue = (random.nextInt(50) + 1) * 100;   // 100~5,000
                    ps.setInt(7, maxIssue);
                    ps.setInt(8, random.nextInt(3) + 1); // 1~3

                    // issued_count: status에 따라
                    String status = generateStatus();
                    int issuedCount;
                    if ("EXPIRED".equals(status)) {
                        issuedCount = (int) (maxIssue * (0.3 + random.nextDouble() * 0.7)); // 30~100%
                    } else if ("ACTIVE".equals(status)) {
                        issuedCount = (int) (maxIssue * random.nextDouble() * 0.8); // 0~80%
                    } else {
                        issuedCount = (int) (maxIssue * random.nextDouble() * 0.3); // 0~30%
                    }
                    ps.setInt(9, issuedCount);

                    int validDays = random.nextInt(84) + 7; // 7~90일
                    ZonedDateTime validFrom = now.minusDays(random.nextInt(365));
                    ZonedDateTime validTo = validFrom.plusDays(validDays);
                    ps.setTimestamp(10, Timestamp.from(validFrom.toInstant()));
                    ps.setTimestamp(11, Timestamp.from(validTo.toInstant()));

                    ps.setString(12, status);
                    ps.setTimestamp(13, Timestamp.from(validFrom.toInstant()));
                    ps.setTimestamp(14, Timestamp.from(now.toInstant()));
                    ps.setNull(15, Types.TIMESTAMP);
                }

                @Override
                public int getBatchSize() {
                    return TEMPLATE_COUNT;
                }
            }
        );

        log.info("[CouponTemplateSeeder] {}건 생성 완료 ({}ms)", TEMPLATE_COUNT, System.currentTimeMillis() - start);
    }

    /** ACTIVE 60% / EXPIRED 30% / INACTIVE 10% */
    private String generateStatus() {
        double r = random.nextDouble();
        if (r < 0.60) return "ACTIVE";
        if (r < 0.90) return "EXPIRED";
        return "INACTIVE";
    }
}
