package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IssuedCouponServiceIntegrationTest {

    @Autowired
    private IssuedCouponService issuedCouponService;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 미사용_발급쿠폰_연쇄삭제 {

        @Test
        void 미사용_발급쿠폰이_삭제된다() {
            IssuedCoupon available = issuedCouponRepository.save(IssuedCoupon.create(1L, 100L));

            issuedCouponService.deleteAvailableByCouponId(1L);

            IssuedCoupon found = issuedCouponRepository.findAllByCouponId(1L).get(0);
            assertThat(found.isDeleted()).isTrue();
        }

        @Test
        void 사용된_발급쿠폰은_보존된다() {
            IssuedCoupon used = issuedCouponRepository.save(IssuedCoupon.create(1L, 100L));
            used.use();
            issuedCouponRepository.save(used);

            issuedCouponService.deleteAvailableByCouponId(1L);

            IssuedCoupon found = issuedCouponRepository.findAllByCouponId(1L).get(0);
            assertThat(found.isDeleted()).isFalse();
        }

        @Test
        void 발급쿠폰이_없으면_정상_처리된다() {
            issuedCouponService.deleteAvailableByCouponId(999L);
        }
    }
}
