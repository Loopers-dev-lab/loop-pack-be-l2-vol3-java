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
 * inventories 시딩 — products와 1:1 매핑
 */
@Component
class InventorySeeder {

    private static final Logger log = LoggerFactory.getLogger(InventorySeeder.class);
    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(42);

    InventorySeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        int productCount = ProductSeeder.PRODUCT_COUNT;
        log.info("[InventorySeeder] {}건 생성 시작", productCount);
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        for (int batchStart = 0; batchStart < productCount; batchStart += BATCH_SIZE) {
            int currentBatchStart = batchStart;
            int currentBatchSize = Math.min(BATCH_SIZE, productCount - batchStart);

            jdbcTemplate.batchUpdate(
                "INSERT INTO inventories (product_id, quantity, reserved_qty, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        long productId = currentBatchStart + i + 1;
                        int quantity = random.nextInt(991) + 10; // 10~1000
                        int reservedQty = quantity / 10; // 10% 예약

                        ps.setLong(1, productId);
                        ps.setInt(2, quantity);
                        ps.setInt(3, reservedQty);
                        ps.setTimestamp(4, Timestamp.from(now.toInstant()));
                        ps.setTimestamp(5, Timestamp.from(now.toInstant()));
                    }

                    @Override
                    public int getBatchSize() {
                        return currentBatchSize;
                    }
                }
            );
        }

        log.info("[InventorySeeder] {}건 생성 완료 ({}ms)", productCount, System.currentTimeMillis() - start);
    }
}
