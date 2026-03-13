package com.loopers.support.perf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 성능 테스트용 상품 시드 데이터 삽입.
 * <ul>
 *   <li>배치 사이즈: 2,000~5,000</li>
 *   <li>2만~5만 건마다 commit / 새 트랜잭션</li>
 *   <li>brand 수백 개 먼저 insert 후 product batch</li>
 *   <li>brand_id, price, stock_quantity, created_at 다양하게 분포</li>
 * </ul>
 * <p>실행: {@code --spring.profiles.active=local,perf-seed} (local로 DB/Redis 설정 로드).
 *     상품 건수: {@code --product.seed.count=100000} 또는 {@code -Dproduct.seed.count=100000}
 *     시드만 수행 후 프로세스 종료: {@code --perf.seed.exit-after-run=true} (Ctrl+C 불필요)
 */
@Component
@Profile("perf-seed")
public class ProductDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductDataSeeder.class);

    private static final int BRAND_COUNT = 500;
    private static final int BATCH_SIZE = 3_000;
    private static final int COMMIT_UNIT = 30_000;
    private static final int DEFAULT_PRODUCT_COUNT = 100_000;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final Environment environment;

    public ProductDataSeeder(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        int productCount = getProductCount(args);
        log.info("perf-seed 시작: brand {}개, product {}건 (batch={}, commit단위={})",
                BRAND_COUNT, productCount, BATCH_SIZE, COMMIT_UNIT);

        seedBrands();
        seedProducts(productCount);

        log.info("perf-seed 완료.");
        if (Boolean.parseBoolean(environment.getProperty("perf.seed.exit-after-run", "false"))) {
            log.info("perf.seed.exit-after-run=true → JVM 종료.");
            System.exit(0);
        }
    }

    private static int getProductCount(ApplicationArguments args) {
        List<String> values = args.getOptionValues("product.seed.count");
        String count = values != null && !values.isEmpty()
                ? values.get(0)
                : System.getProperty("product.seed.count", String.valueOf(DEFAULT_PRODUCT_COUNT));
        try {
            return Integer.parseInt(count);
        } catch (NumberFormatException e) {
            return DEFAULT_PRODUCT_COUNT;
        }
    }

    private void seedBrands() {
        String sql = "INSERT INTO brand (name, created_at, updated_at, deleted_at) VALUES (?, NOW(), NOW(), NULL)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setString(1, "브랜드-" + (i + 1));
            }

            @Override
            public int getBatchSize() {
                return BRAND_COUNT;
            }
        });
        log.info("brand {}건 삽입 완료.", BRAND_COUNT);
    }

    private void seedProducts(int totalCount) {
        String sql = "INSERT INTO product (brand_id, name, price, stock_quantity, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NULL)";

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int committed = 0;
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        TransactionStatus status = transactionManager.getTransaction(def);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        try {
            for (int i = 0; i < totalCount; i++) {
                long brandId = 1L + (i % BRAND_COUNT);
                String name = "상품-" + (i + 1);
                BigDecimal price = priceDistribution(i, r);
                int stock = 1 + r.nextInt(100);
                ZonedDateTime createdAt = createdAtIndexed(i, totalCount, r);
                Timestamp ts = Timestamp.from(createdAt.toInstant());

                batch.add(new Object[]{brandId, name, price, stock, ts, ts});

                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(sql, batch);
                    batch.clear();
                }

                committed++;
                if (committed >= COMMIT_UNIT) {
                    if (!batch.isEmpty()) {
                        flushBatch(sql, batch);
                        batch.clear();
                    }
                    transactionManager.commit(status);
                    status = transactionManager.getTransaction(def);
                    committed = 0;
                }
            }

            if (!batch.isEmpty()) {
                flushBatch(sql, batch);
            }
            transactionManager.commit(status);
        } catch (RuntimeException e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    private static BigDecimal priceDistribution(int index, ThreadLocalRandom r) {
        int segment = index % 10;
        if (segment < 3) {
            return BigDecimal.valueOf(1_000 + r.nextLong(9_000));
        }
        if (segment < 7) {
            return BigDecimal.valueOf(10_000 + r.nextLong(90_000));
        }
        return BigDecimal.valueOf(100_000 + r.nextLong(900_000));
    }

    private static ZonedDateTime createdAtIndexed(int index, int totalCount, ThreadLocalRandom r) {
        int daysAgo = (int) ((long) (365 * index) / Math.max(1, totalCount)) + r.nextInt(3);
        return ZonedDateTime.now(ZoneId.systemDefault()).minusDays(Math.min(365, daysAgo));
    }

    private void flushBatch(String sql, List<Object[]> batch) {
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Object[] row = batch.get(i);
                ps.setLong(1, (Long) row[0]);
                ps.setString(2, (String) row[1]);
                ps.setBigDecimal(3, (BigDecimal) row[2]);
                ps.setInt(4, (Integer) row[3]);
                ps.setTimestamp(5, (Timestamp) row[4]);
                ps.setTimestamp(6, (Timestamp) row[5]);
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }
}
