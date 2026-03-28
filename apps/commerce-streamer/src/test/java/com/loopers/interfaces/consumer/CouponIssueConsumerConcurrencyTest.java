package com.loopers.interfaces.consumer;

import com.loopers.testcontainers.KafkaTestContainersConfig;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, KafkaTestContainersConfig.class})
@DisplayName("CouponIssueConsumer E2E 동시성 테스트 — Kafka → Consumer → DB 수량 제어 검증")
class CouponIssueConsumerConcurrencyTest {

    private static final String TOPIC = "coupon-issue-requests";
    private static final int TOTAL_REQUESTS = 1000;
    private static final int TOTAL_QUANTITY = 300;
    private static final long WAIT_TIMEOUT_MS = 120_000L;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private long templateId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO coupon_templates (total_quantity, issued_count, created_at, updated_at) VALUES (?, 0, NOW(), NOW())",
                TOTAL_QUANTITY
        );
        templateId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
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
    @DisplayName("1000개 이벤트를 Kafka로 발행하면 totalQuantity(300)개만 정확히 발급된다")
    void e2e_exactly_totalQuantity_issued() throws InterruptedException {
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            CouponIssuePayload payload = new CouponIssuePayload(
                    UUID.randomUUID().toString(),
                    "CouponIssueRequested",
                    1,
                    "req-" + i,
                    templateId,
                    (long) (i + 1),
                    LocalDateTime.now()
            );
            kafkaTemplate.send(TOPIC, String.valueOf(templateId), payload);
        }
        kafkaTemplate.flush();

        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Long handled = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_handled", Long.class);
            if (handled != null && handled >= TOTAL_REQUESTS) {
                break;
            }
            Thread.sleep(500);
        }

        Long issuedCount = jdbcTemplate.queryForObject(
                "SELECT issued_count FROM coupon_templates WHERE id = ?", Long.class, templateId);
        Long userCouponCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons", Long.class);

        assertThat(issuedCount).isEqualTo(TOTAL_QUANTITY);
        assertThat(userCouponCount).isEqualTo(TOTAL_QUANTITY);
    }
}
