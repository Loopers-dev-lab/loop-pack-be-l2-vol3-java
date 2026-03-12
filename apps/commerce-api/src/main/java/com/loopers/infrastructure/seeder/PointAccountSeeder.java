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
 * point_accounts 시딩 — users와 1:1 (5,000건)
 *
 * 분포:
 * - balance: 로그분포 (60%가 0~5,000원, 나머지 5,000~50,000원)
 */
@Component
class PointAccountSeeder {

    private static final Logger log = LoggerFactory.getLogger(PointAccountSeeder.class);
    private static final int USER_COUNT = UserSeeder.USER_COUNT;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(99);

    PointAccountSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[PointAccountSeeder] {}건 생성 시작", USER_COUNT);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.batchUpdate(
            "INSERT INTO point_accounts (user_id, balance, created_at, updated_at, deleted_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    long userId = i + 1;
                    int balance = generateBalance();

                    ps.setLong(1, userId);
                    ps.setInt(2, balance);
                    ps.setTimestamp(3, Timestamp.from(now.toInstant()));
                    ps.setTimestamp(4, Timestamp.from(now.toInstant()));
                    ps.setNull(5, java.sql.Types.TIMESTAMP);
                }

                @Override
                public int getBatchSize() {
                    return USER_COUNT;
                }
            }
        );

        log.info("[PointAccountSeeder] {}건 생성 완료 ({}ms)", USER_COUNT, System.currentTimeMillis() - start);
    }

    /** 60%: 0~5,000원 / 25%: 5,001~20,000원 / 15%: 20,001~50,000원 */
    private int generateBalance() {
        double r = random.nextDouble();
        if (r < 0.60) return random.nextInt(5_001);
        if (r < 0.85) return random.nextInt(15_000) + 5_001;
        return random.nextInt(30_000) + 20_001;
    }
}
