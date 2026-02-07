package com.loopers.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MySqlConnectionLogger {

    private static final Logger log = LoggerFactory.getLogger(MySqlConnectionLogger.class);

    private final JdbcTemplate jdbcTemplate;

    public MySqlConnectionLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectionStatus() {
        try {
            // MySQL에 쿼리를 날려서 container 로그에 기록 남김
            String result = jdbcTemplate.queryForObject(
                "SELECT CONCAT('Connected from Spring Boot at ', NOW()) as message",
                String.class
            );
            log.info("✅ MySQL Connection SUCCESS: {}", result);

            // 추가로 version도 확인
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            log.info("   └─ MySQL Version: {}", version);
        } catch (Exception e) {
            log.error("❌ MySQL Connection FAILED", e);
        }
    }
}
