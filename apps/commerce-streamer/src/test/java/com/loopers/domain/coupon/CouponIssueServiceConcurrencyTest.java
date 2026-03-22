package com.loopers.domain.coupon;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("CouponIssueService 선착순 동시성 테스트 — DB 레이어 수량 제어 검증")
class CouponIssueServiceConcurrencyTest {

    private static final int TOTAL_REQUESTS = 1000;
    private static final int TOTAL_QUANTITY = 300;
    // test profile pool max=10 → 풀 크기에 맞춰 스레드 수 제한, 1000개 요청은 큐잉 처리
    private static final int THREAD_POOL_SIZE = 10;

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private long templateId;
    private final List<String> requestIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO coupon_templates (total_quantity, issued_count, created_at, updated_at) VALUES (?, 0, NOW(), NOW())",
                TOTAL_QUANTITY
        );
        templateId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            String requestId = "req-" + i;
            requestIds.add(requestId);
            jdbcTemplate.update(
                    "INSERT INTO coupon_issue_requests (request_id, ref_coupon_template_id, ref_member_id, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'PENDING', NOW(), NOW())",
                    requestId, templateId, (long) (i + 1)
            );
        }
    }

    @AfterEach
    void tearDown() {
        requestIds.clear();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("1000명이 요청하면 totalQuantity(300)개만 정확히 발급된다")
    void concurrency_exactly_totalQuantity_issued() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch done = new CountDownLatch(TOTAL_REQUESTS);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    couponIssueService.processIssue(
                            UUID.randomUUID().toString(),
                            requestIds.get(idx),
                            templateId,
                            (long) (idx + 1)
                    );
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        Long issuedCount = jdbcTemplate.queryForObject(
                "SELECT issued_count FROM coupon_templates WHERE id = ?", Long.class, templateId);
        Long userCouponCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons", Long.class);

        assertThat(issuedCount).isEqualTo(TOTAL_QUANTITY);
        assertThat(userCouponCount).isEqualTo(TOTAL_QUANTITY);
    }
}
