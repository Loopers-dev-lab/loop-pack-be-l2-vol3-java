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

@Component
class BrandSeeder {

    private static final Logger log = LoggerFactory.getLogger(BrandSeeder.class);
    static final int BRAND_COUNT = 500;
    static final int ACTIVE_BRAND_COUNT = 400;

    private final JdbcTemplate jdbcTemplate;

    BrandSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[BrandSeeder] {}개 브랜드 생성 시작", BRAND_COUNT);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.batchUpdate(
            "INSERT INTO brands (name, description, status, created_at, updated_at, deleted_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = i + 1;
                    ps.setString(1, "브랜드" + idx);
                    ps.setString(2, "브랜드" + idx + " 설명");

                    // 1~400: ACTIVE, 401~500: INACTIVE
                    String status = idx <= ACTIVE_BRAND_COUNT ? "ACTIVE" : "INACTIVE";
                    ps.setString(3, status);

                    ps.setTimestamp(4, Timestamp.from(now.toInstant()));
                    ps.setTimestamp(5, Timestamp.from(now.toInstant()));

                    // 491~500번 브랜드: soft delete (10개)
                    if (idx > 490) {
                        ps.setTimestamp(6, Timestamp.from(now.toInstant()));
                    } else {
                        ps.setNull(6, java.sql.Types.TIMESTAMP);
                    }
                }

                @Override
                public int getBatchSize() {
                    return BRAND_COUNT;
                }
            }
        );

        log.info("[BrandSeeder] {}개 브랜드 생성 완료 ({}ms)", BRAND_COUNT, System.currentTimeMillis() - start);
    }
}
