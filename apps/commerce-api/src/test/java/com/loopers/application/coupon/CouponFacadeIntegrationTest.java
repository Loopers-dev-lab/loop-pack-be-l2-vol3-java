package com.loopers.application.coupon;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.user.User;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponFacadeIntegrationTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 쿠폰_발급_내역_조회 {

        @Test
        void 만료된_쿠폰의_미사용_발급건은_상태가_만료로_판정된다() {
            User user = userService.signUp(UserCommand.SignUp.of(
                    "testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com"
            ));
            Coupon coupon = couponRepository.save(
                    Coupon.create("쿠폰", CouponType.FIXED, 1000, null, 100, LocalDateTime.now().plusSeconds(1))
            );
            issuedCouponRepository.save(IssuedCoupon.create(coupon.getId(), user.getId()));

            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            Page<IssuedCouponAdminInfo> result = couponFacade.getCouponIssues(coupon.getId(), PageRequest.of(0, 20));

            assertThat(result.getContent().get(0).status()).isEqualTo("EXPIRED");
        }
    }

    @Nested
    class 내_쿠폰_목록_조회 {

        @Test
        void 만료된_쿠폰의_미사용_발급건은_상태가_만료로_판정된다() {
            Coupon coupon = couponRepository.save(
                    Coupon.create("쿠폰", CouponType.FIXED, 1000, null, 100, LocalDateTime.now().plusSeconds(1))
            );
            IssuedCoupon issuedCoupon = issuedCouponRepository.save(IssuedCoupon.create(coupon.getId(), 1L));

            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            Page<IssuedCouponInfo> result = couponFacade.getMyCoupons(1L, PageRequest.of(0, 20));

            assertThat(result.getContent().get(0).status()).isEqualTo("EXPIRED");
        }
    }
}
