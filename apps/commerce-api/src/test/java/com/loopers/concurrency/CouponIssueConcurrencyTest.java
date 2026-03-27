package com.loopers.concurrency;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("선착순 쿠폰: maxIssuanceCount보다 많은 동시 요청이 와도 수량 초과 발급이 발생하지 않는다")
    @Test
    void concurrentCouponIssue_doesNotExceedMaxIssuanceCount() throws InterruptedException {
        // arrange
        int maxIssuance = 100;
        int threadCount = 200;

        Coupon coupon = couponRepository.save(
            new Coupon("선착순 할인", DiscountType.FIXED, 5000, 0, ZonedDateTime.now().plusDays(30)));
        Long couponId = coupon.getId();

        // maxIssuanceCount 설정 (Entity에 setter 없으므로 native SQL)
        jdbcTemplate.update("UPDATE coupon SET max_issuance_count = ? WHERE id = ?",
            maxIssuance, couponId);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act: CouponIssueConsumer의 CAS UPDATE를 동시에 실행
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    // CouponIssueConsumer와 동일한 CAS UPDATE
                    int casResult = jdbcTemplate.update(
                        "UPDATE coupon SET issued_count = issued_count + 1 "
                            + "WHERE id = ? "
                            + "AND (max_issuance_count IS NULL OR issued_count < max_issuance_count) "
                            + "AND deleted_at IS NULL",
                        couponId
                    );

                    if (casResult > 0) {
                        jdbcTemplate.update(
                            "INSERT INTO coupon_issue (coupon_id, member_id, status, expired_at, created_at) "
                                + "SELECT ?, ?, 'AVAILABLE', c.expired_at, NOW(6) "
                                + "FROM coupon c WHERE c.id = ?",
                            couponId, memberId, couponId
                        );
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert
        Integer issuedCount = jdbcTemplate.queryForObject(
            "SELECT issued_count FROM coupon WHERE id = ?", Integer.class, couponId);
        Integer couponIssueCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM coupon_issue WHERE coupon_id = ?", Integer.class, couponId);

        assertThat(successCount.get()).isEqualTo(maxIssuance);
        assertThat(failCount.get()).isEqualTo(threadCount - maxIssuance);
        assertThat(issuedCount).isEqualTo(maxIssuance);
        assertThat(couponIssueCount).isEqualTo(maxIssuance);
    }

    @DisplayName("선착순 쿠폰: maxIssuanceCount가 없으면 제한 없이 발급된다")
    @Test
    void concurrentCouponIssue_withoutLimit_allSucceed() throws InterruptedException {
        // arrange
        int threadCount = 100;

        Coupon coupon = couponRepository.save(
            new Coupon("무제한 할인", DiscountType.FIXED, 5000, 0, ZonedDateTime.now().plusDays(30)));
        Long couponId = coupon.getId();
        // maxIssuanceCount는 null (기본값)

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    int casResult = jdbcTemplate.update(
                        "UPDATE coupon SET issued_count = issued_count + 1 "
                            + "WHERE id = ? "
                            + "AND (max_issuance_count IS NULL OR issued_count < max_issuance_count) "
                            + "AND deleted_at IS NULL",
                        couponId
                    );
                    if (casResult > 0) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert: 제한 없으므로 모두 성공
        Integer issuedCount = jdbcTemplate.queryForObject(
            "SELECT issued_count FROM coupon WHERE id = ?", Integer.class, couponId);

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(issuedCount).isEqualTo(threadCount);
    }
}
