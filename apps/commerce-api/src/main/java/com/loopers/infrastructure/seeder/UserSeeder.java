package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    static final String SEEDING_RAW_PASSWORD = "Hx7!mK2@";

    private static final String[] LAST_NAME = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};
    private static final String[] FIRST_NAME = {"민준", "서연", "도윤", "하윤", "서준", "지우", "예준", "수아", "주원", "지호"};

    private final JdbcTemplate jdbcTemplate;

    UserSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[UserSeeder] {}명 생성 시작", USER_COUNT);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String encodedPassword = new BCryptPasswordEncoder().encode(SEEDING_RAW_PASSWORD);

        jdbcTemplate.batchUpdate(
            "INSERT INTO users (login_id, password, name, birth_date, email, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = i + 1;
                    ps.setString(1, "user" + idx);
                    ps.setString(2, encodedPassword);
                    ps.setString(3, LAST_NAME[i % LAST_NAME.length] + FIRST_NAME[i % FIRST_NAME.length]);
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
