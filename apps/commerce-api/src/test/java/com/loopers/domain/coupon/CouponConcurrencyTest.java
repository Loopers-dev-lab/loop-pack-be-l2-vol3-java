package com.loopers.domain.coupon;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CouponConcurrencyTest {
    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private CouponService couponService;

    @Autowired
    DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_NAME = "namjin";
    private static final String VALID_BIRTHDAY = "1994-05-25";
    private static final String VALID_EMAIL = "epemxksl@gmail.com";
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);
    private static final int THREAD_COUNT = 10;
    private static final int ORDER_AMOUNT = 20000;

    @DisplayName("회원이 자신의 쿠폰 사용 시")
    @Nested
    class UseCoupon {
        @DisplayName("동시에 여러기기에서 쿠폰을 사용해도 하나만 성공한다.")
        @Test
        void onlyOneTransactionComplete_whenManyRequests() throws InterruptedException {
            // arrange
            UserModel user = userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL));
            CouponTemplate couponTemplate = couponTemplateJpaRepository.save(new CouponTemplate("정액 쿠폰", CouponType.FIXED, 1000, 10000, FUTURE_EXPIRED_AT));
            UserCoupon coupon = userCouponJpaRepository.save(new UserCoupon(couponTemplate.getId(), user.getId(), couponTemplate.getExpiredAt()));

            // act
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드를 대기기 시키고 동시에 applyCoupon 작업을 시작할 수 있도록
                        couponService.applyCoupon(coupon.getId(), user.getId(), ORDER_AMOUNT);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 예상된 실패 (이미 사용된 쿠폰)
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            // startLatch의 카운트 다운은 메인스레드에 존재하기 때문에, 반복문이 다 실행되고 아래 코드가 실행이 됨.
            // 그때 비로소 10개의 대기중인 백그라운드 코드가 동시에 실행이 됨.
            startLatch.countDown(); // 모든 스레드 동시 출발
            doneLatch.await();
            executor.shutdown();

            // assert
            UserCoupon used = userCouponJpaRepository.findById(coupon.getId()).orElseThrow();
            assertThat(successCount.get()).isEqualTo(1);      // 정확히 1번만 성공
            assertThat(used.getUsedAt()).isNotNull();          // DB에 used_at 기록 확인
        }
    }
}
