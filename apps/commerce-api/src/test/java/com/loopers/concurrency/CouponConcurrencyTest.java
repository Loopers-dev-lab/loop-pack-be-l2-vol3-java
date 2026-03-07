package com.loopers.concurrency;

import com.loopers.application.coupon.CouponCommand;
import com.loopers.application.coupon.CouponService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.ConcurrencyTestHelper;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    void 동시_발급_요청에도_발급_수량이_정확히_관리된다() throws InterruptedException {
        Coupon coupon = couponService.register(CouponCommand.Register.of(
                "쿠폰", "FIXED", 1000,
                null, 100, LocalDateTime.now().plusDays(7)
        ));

        int threadCount = 10;
        Queue<Exception> exceptions = ConcurrencyTestHelper.executeConcurrently(threadCount, i ->
                couponService.issue(coupon.getId())
        );

        Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(found.getIssuedCount()).isEqualTo(threadCount);
        assertThat(exceptions).isEmpty();
    }

    @Test
    void 최대_발급_수량보다_많은_동시_발급_요청이_들어오면_일부만_성공한다() throws InterruptedException {
        int maxIssueCount = 5;
        Coupon coupon = couponService.register(CouponCommand.Register.of(
                "쿠폰", "FIXED", 1000,
                null, maxIssueCount, LocalDateTime.now().plusDays(7)
        ));

        int threadCount = 10;
        Queue<Exception> exceptions = ConcurrencyTestHelper.executeConcurrently(threadCount, i ->
                couponService.issue(coupon.getId())
        );

        Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(found.getIssuedCount()).isEqualTo(maxIssueCount);
        assertThat(exceptions).hasSize(threadCount - maxIssueCount);
    }
}
