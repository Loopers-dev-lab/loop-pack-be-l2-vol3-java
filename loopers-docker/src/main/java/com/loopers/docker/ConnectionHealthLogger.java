package com.loopers.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class ConnectionHealthLogger {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHealthLogger.class);

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public ConnectionHealthLogger(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectionStatus() {
        logMySQLConnection();
        logRedisConnection();
    }

    private void logMySQLConnection() {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            String userName = connection.getMetaData().getUserName();
            log.info("✅ MySQL Connection SUCCESS - URL: {}, User: {}", url, userName);
        } catch (Exception e) {
            log.error("❌ MySQL Connection FAILED", e);
        }
    }

    private void logRedisConnection() {
        try {
            redisConnectionFactory.getConnection().ping();
            log.info("✅ Redis Connection SUCCESS");
        } catch (Exception e) {
            log.error("❌ Redis Connection FAILED", e);
        }
    }


}
