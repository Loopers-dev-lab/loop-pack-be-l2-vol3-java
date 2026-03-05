package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IssuedCouponServiceIntegrationTest {

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

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
    class 쿠폰_발급 {

        @Test
        void 유효한_쿠폰ID와_사용자ID로_발급하면_발급쿠폰이_생성된다() {
            IssuedCoupon result = issuedCouponService.issue(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE);

            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getCouponId()).isEqualTo(1L),
                    () -> assertThat(result.getUserId()).isEqualTo(100L),
                    () -> assertThat(result.isUsed()).isFalse(),
                    () -> assertThat(result.getCouponName()).isEqualTo("테스트 쿠폰"),
                    () -> assertThat(result.getCouponType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(result.getCouponValue()).isEqualTo(1000),
                    () -> assertThat(result.getMinOrderAmount()).isNull(),
                    () -> assertThat(result.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @Test
        void 이미_발급받은_쿠폰이면_예외() {
            issuedCouponService.issue(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE);

            assertThatThrownBy(() -> issuedCouponService.issue(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT);
                        assertThat(e.getMessage()).contains("이미 발급받은 쿠폰입니다");
                    });
        }

        @Test
        void 같은_쿠폰이라도_다른_사용자는_발급_가능하다() {
            issuedCouponService.issue(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE);

            IssuedCoupon result = issuedCouponService.issue(1L, 200L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getUserId()).isEqualTo(200L);
        }
    }

    @Nested
    class 미사용_발급쿠폰_연쇄삭제 {

        @Test
        void 미사용_발급쿠폰이_삭제된다() {
            issuedCouponRepository.save(IssuedCoupon.create(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE));

            issuedCouponService.deleteAvailableByCouponId(1L);

            IssuedCoupon found = issuedCouponRepository.findAllByCouponId(1L).get(0);
            assertThat(found.isDeleted()).isTrue();
        }

        @Test
        void 사용된_발급쿠폰은_보존된다() {
            IssuedCoupon used = issuedCouponRepository.save(IssuedCoupon.create(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE));
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
