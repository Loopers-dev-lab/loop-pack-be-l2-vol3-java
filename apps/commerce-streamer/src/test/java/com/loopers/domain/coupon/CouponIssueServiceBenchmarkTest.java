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

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("CouponIssueService processIssue 벤치마크 테스트")
class CouponIssueServiceBenchmarkTest {

    private static final int SEQUENTIAL_OPS = 500;
    private static final long MAX_POLL_INTERVAL_MS = 120_000L;

    private long templateId;

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO coupon_templates (total_quantity, issued_count, created_at, updated_at) " +
                "VALUES (?, 0, NOW(), NOW())",
                SEQUENTIAL_OPS * 2
        );
        templateId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (int i = 0; i < SEQUENTIAL_OPS; i++) {
            jdbcTemplate.update(
                    "INSERT INTO coupon_issue_requests (request_id, ref_coupon_template_id, ref_member_id, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'PENDING', NOW(), NOW())",
                    "req-" + i, templateId, (long) (i + 1)
            );
        }
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("순차 processIssue 500건 — p50/p99 측정 및 MAX_POLL_RECORDS 도출")
    void sequential_processIssue_benchmark() {
        long[] latencies = new long[SEQUENTIAL_OPS];

        for (int i = 0; i < SEQUENTIAL_OPS; i++) {
            String eventId = UUID.randomUUID().toString();
            String requestId = "req-" + i;
            long memberId = (long) (i + 1);

            long start = System.nanoTime();
            couponIssueService.processIssue(requestId, templateId, memberId);
            latencies[i] = System.nanoTime() - start;
        }

        long[] sorted = Arrays.copyOf(latencies, latencies.length);
        Arrays.sort(sorted);

        long p50Ms = sorted[(int) (SEQUENTIAL_OPS * 0.50)] / 1_000_000;
        long p99Ms = sorted[(int) (SEQUENTIAL_OPS * 0.99)] / 1_000_000;
        long maxPollRecords = (long) (MAX_POLL_INTERVAL_MS * 0.7 / Math.max(p99Ms, 1));

        System.out.printf("[CouponIssue Sequential] p50=%dms, p99=%dms, MAX_POLL_RECORDS_FORMULA=%d%n",
                p50Ms, p99Ms, maxPollRecords);

        assertThat(p99Ms).isLessThan(2000);
        assertThat(maxPollRecords).isGreaterThan(0);
    }
}
