package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 쿠폰_등록 {

        @Test
        void 유효한_정보로_등록하면_쿠폰이_생성된다() {
            CouponCommand.Register command = CouponCommand.Register.of(
                    "1000원 할인", CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );

            Coupon result = couponService.register(command);

            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getName()).isEqualTo("1000원 할인"),
                    () -> assertThat(result.getType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(result.getValue()).isEqualTo(1000),
                    () -> assertThat(result.getMinOrderAmount()).isEqualTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(result.getMaxIssueCount()).isEqualTo(100),
                    () -> assertThat(result.getIssuedCount()).isEqualTo(0)
            );
        }

        @Test
        void 정률_타입으로_등록하면_쿠폰이_생성된다() {
            CouponCommand.Register command = CouponCommand.Register.of(
                    "10% 할인", CouponType.RATE, 10,
                    null, 50, LocalDateTime.now().plusDays(7)
            );

            Coupon result = couponService.register(command);

            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getType()).isEqualTo(CouponType.RATE),
                    () -> assertThat(result.getValue()).isEqualTo(10),
                    () -> assertThat(result.getMinOrderAmount()).isNull()
            );
        }
    }

    @Nested
    class 쿠폰_삭제 {

        @Test
        void 활성_쿠폰을_삭제하면_삭제_상태로_변경된다() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "1000원 할인", CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            ));

            couponService.delete(coupon.getId());

            Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }

        @Test
        void 미존재_쿠폰이면_예외() {
            assertThatThrownBy(() -> couponService.delete(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        void 삭제된_쿠폰을_다시_삭제해도_멱등하게_처리된다() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            ));
            couponService.delete(coupon.getId());

            couponService.delete(coupon.getId());

            Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }
    }

    @Nested
    class 쿠폰_발급 {

        @Test
        void 유효한_쿠폰에_발급하면_발급수량이_1_증가한다() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "1000원 할인", CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            ));

            couponService.issue(coupon.getId());

            Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(found.getIssuedCount()).isEqualTo(1);
        }

        @Test
        void 미존재_쿠폰이면_예외() {
            assertThatThrownBy(() -> couponService.issue(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 삭제된_쿠폰이면_예외() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            ));
            coupon.delete();
            couponRepository.save(coupon);

            assertThatThrownBy(() -> couponService.issue(coupon.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 만료된_쿠폰이면_예외() {
            Coupon coupon = couponRepository.save(
                    Coupon.create("쿠폰", CouponType.FIXED, 1000, null, 100, LocalDateTime.now().plusSeconds(1))
            );
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            assertThatThrownBy(() -> couponService.issue(coupon.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 발급_수량이_소진되면_예외() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 1, LocalDateTime.now().plusDays(7)
            ));
            couponService.issue(coupon.getId());

            assertThatThrownBy(() -> couponService.issue(coupon.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 동시_발급_요청에도_발급_수량이_정확히_관리된다() throws InterruptedException {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            ));
            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        couponService.issue(coupon.getId());
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(found.getIssuedCount()).isEqualTo(threadCount);
            assertThat(exceptions).isEmpty();
        }
    }

    @Nested
    class 쿠폰_수정 {

        @Test
        void 유효한_정보로_수정하면_수정된_쿠폰이_반환된다() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "1000원 할인", CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            ));
            CouponCommand.UpdateInfo command = CouponCommand.UpdateInfo.of(
                    null, "2000원 할인", 2000, BigDecimal.valueOf(20000), null, null
            );

            Coupon result = couponService.updateInfo(coupon.getId(), command);

            assertAll(
                    () -> assertThat(result.getName()).isEqualTo("2000원 할인"),
                    () -> assertThat(result.getValue()).isEqualTo(2000),
                    () -> assertThat(result.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(result.getMaxIssueCount()).isEqualTo(100)
            );
        }

        @Test
        void 존재하지_않는_쿠폰을_수정하면_예외() {
            CouponCommand.UpdateInfo command = CouponCommand.UpdateInfo.of(
                    null, "수정된 이름", null, null, null, null
            );

            assertThatThrownBy(() -> couponService.updateInfo(999L, command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        void 삭제된_쿠폰을_수정하면_예외() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            ));
            coupon.delete();
            couponRepository.save(coupon);
            CouponCommand.UpdateInfo command = CouponCommand.UpdateInfo.of(
                    null, "수정된 이름", null, null, null, null
            );

            assertThatThrownBy(() -> couponService.updateInfo(coupon.getId(), command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        void 최대_발급_수량을_현재_발급_수량보다_작게_설정하면_예외() {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            ));
            couponService.issue(coupon.getId());
            couponService.issue(coupon.getId());
            CouponCommand.UpdateInfo command = CouponCommand.UpdateInfo.of(
                    null, null, null, null, 1, null
            );

            assertThatThrownBy(() -> couponService.updateInfo(coupon.getId(), command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
