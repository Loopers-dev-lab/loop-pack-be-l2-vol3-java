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
class UserSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);
    static final int USER_COUNT = 5_000;

    private final JdbcTemplate jdbcTemplate;

    UserSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[UserSeeder] {}명 생성 시작", USER_COUNT);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.batchUpdate(
            "INSERT INTO users (login_id, password, name, birth_date, email, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = i + 1;
                    ps.setString(1, "user" + idx);
                    ps.setString(2, "$2a$10$dummyHashedPasswordForSeeding");
                    ps.setString(3, "테스트유저" + idx);
                    ps.setDate(4, java.sql.Date.valueOf("1990-01-01"));
                    ps.setString(5, "user" + idx + "@test.com");
                    ps.setTimestamp(6, Timestamp.from(now.toInstant()));
                    ps.setTimestamp(7, Timestamp.from(now.toInstant()));
                }

                @Override
                public int getBatchSize() {
                    return USER_COUNT;
                }
            }
        );

        log.info("[UserSeeder] {}명 생성 완료 ({}ms)", USER_COUNT, System.currentTimeMillis() - start);
    }
}
