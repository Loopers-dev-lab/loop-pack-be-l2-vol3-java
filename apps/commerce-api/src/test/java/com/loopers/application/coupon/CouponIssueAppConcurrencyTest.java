package com.loopers.application.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssueApp.requestIssue() 동시 중복 요청 race condition 검증
 *
 * 문제:
 *   existsBy 체크와 save 사이에 다른 트랜잭션이 끼어들면
 *   두 트랜잭션 모두 existsBy=false 통과 → 중복 PENDING 레코드 2건 생성 가능.
 *   두 개의 Outbox 이벤트 → 서로 다른 eventId → streamer 멱등성 체크 통과 → 중복 처리 시도.
 *
 * 기대 동작:
 *   (ref_coupon_template_id, ref_member_id) unique constraint + saveAndFlush + 예외 catch
 *   → 동시에 30개 요청이 들어와도 coupon_issue_requests = 1건, outbox = 1건
 *   → 나머지 29건은 CONFLICT 에러
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("CouponIssueApp 동시 중복 요청 race condition 검증")
class CouponIssueAppConcurrencyTest {

    private static final int THREAD_COUNT = 30;
    private static final long MEMBER_ID = 9001L;

    @Autowired
    private CouponIssueApp couponIssueApp;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private long templateId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
            "INSERT INTO coupon_templates (name, type, value, min_order_amount, expired_at, total_quantity, issued_count, deleted_at, created_at, updated_at) " +
            "VALUES ('race-condition-test', 'FIXED', 1000, 5000, DATE_ADD(NOW(), INTERVAL 7 DAY), 100, 0, NULL, NOW(), NOW())"
        );
        templateId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("[RACE] 동일 (memberId, templateId) 동시 30개 요청 → 1건만 성공, 나머지 CONFLICT")
    void concurrentRequestIssue_sameMemberAndTemplate_onlyOneSucceeds() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    couponIssueApp.requestIssue(templateId, MEMBER_ID);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(e);
                    }
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long requestCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM coupon_issue_requests WHERE ref_member_id = " + MEMBER_ID, Long.class);
        long outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE aggregate_type = 'coupon_issue_request'", Long.class);

        System.out.printf(
            "[RACE-CONCURRENT] threads=%d | success=%d | conflict=%d | unexpected=%d%n" +
            "  coupon_issue_requests=%d | outbox=%d%n" +
            "  → unique constraint이 있으면: success=1, conflict=29, requests=1, outbox=1%n" +
            "  → unique constraint이 없으면: success>1, requests>1 (race condition 재현)%n",
            THREAD_COUNT, successCount.get(), conflictCount.get(), unexpectedErrors.size(),
            requestCount, outboxCount
        );

        assertThat(unexpectedErrors).as("예상치 못한 에러 없어야 함").isEmpty();
        assertThat(successCount.get()).as("정확히 1건만 성공").isEqualTo(1);
        assertThat(conflictCount.get()).as("나머지 " + (THREAD_COUNT - 1) + "건은 CONFLICT").isEqualTo(THREAD_COUNT - 1);
        assertThat(requestCount).as("coupon_issue_requests = 1건").isEqualTo(1L);
        assertThat(outboxCount).as("outbox = 1건").isEqualTo(1L);
    }
}
