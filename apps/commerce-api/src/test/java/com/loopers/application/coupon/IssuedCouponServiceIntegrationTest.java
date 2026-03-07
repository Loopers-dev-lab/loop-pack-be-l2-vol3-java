package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

    @BeforeEach
    void setUp() {
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
            IssuedCoupon saved = issuedCouponRepository.save(IssuedCoupon.create(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE));

            issuedCouponService.deleteAvailableByCouponId(1L);

            IssuedCoupon found = issuedCouponRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }

        @Test
        void 사용된_발급쿠폰은_보존된다() {
            IssuedCoupon used = issuedCouponRepository.save(IssuedCoupon.create(1L, 100L, "테스트 쿠폰",
                    CouponType.FIXED, 1000, null, FUTURE));
            used.use();
            issuedCouponRepository.save(used);

            issuedCouponService.deleteAvailableByCouponId(1L);

            IssuedCoupon found = issuedCouponRepository.findById(used.getId()).orElseThrow();
            assertThat(found.isDeleted()).isFalse();
        }

        @Test
        void 발급쿠폰이_없으면_정상_처리된다() {
            issuedCouponService.deleteAvailableByCouponId(999L);
        }
    }

    @Nested
    class 쿠폰별_발급_내역_조회 {

        @Test
        void 해당_쿠폰의_발급_내역만_조회된다() {
            issuedCouponService.issue(1L, 100L, "쿠폰A", CouponType.FIXED, 1000, null, FUTURE);
            issuedCouponService.issue(1L, 200L, "쿠폰A", CouponType.FIXED, 1000, null, FUTURE);
            issuedCouponService.issue(2L, 100L, "쿠폰B", CouponType.FIXED, 2000, null, FUTURE);

            Page<IssuedCoupon> result = issuedCouponService.findByCouponId(1L, PageRequest.of(0, 20));

            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(2),
                    () -> assertThat(result.getContent()).allMatch(ic -> ic.getCouponId().equals(1L))
            );
        }

        @Test
        void 발급_내역이_없으면_빈_페이지를_반환한다() {
            Page<IssuedCoupon> result = issuedCouponService.findByCouponId(999L, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    class 사용자별_활성_발급쿠폰_조회 {

        @Test
        void 해당_사용자의_활성_발급쿠폰만_조회된다() {
            issuedCouponService.issue(1L, 100L, "쿠폰A", CouponType.FIXED, 1000, null, FUTURE);
            issuedCouponService.issue(2L, 100L, "쿠폰B", CouponType.FIXED, 2000, null, FUTURE);
            issuedCouponService.issue(3L, 200L, "쿠폰C", CouponType.FIXED, 3000, null, FUTURE);

            Page<IssuedCoupon> result = issuedCouponService.findActiveByUserId(100L, PageRequest.of(0, 20));

            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(2),
                    () -> assertThat(result.getContent()).allMatch(ic -> ic.getUserId().equals(100L))
            );
        }

        @Test
        void 삭제된_발급쿠폰은_제외된다() {
            issuedCouponService.issue(1L, 100L, "쿠폰A", CouponType.FIXED, 1000, null, FUTURE);
            issuedCouponService.issue(2L, 100L, "쿠폰B", CouponType.FIXED, 2000, null, FUTURE);
            issuedCouponService.deleteAvailableByCouponId(2L);

            Page<IssuedCoupon> result = issuedCouponService.findActiveByUserId(100L, PageRequest.of(0, 20));

            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(1),
                    () -> assertThat(result.getContent().get(0).getCouponName()).isEqualTo("쿠폰A")
            );
        }

        @Test
        void 발급쿠폰이_없으면_빈_페이지를_반환한다() {
            Page<IssuedCoupon> result = issuedCouponService.findActiveByUserId(999L, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }
}
