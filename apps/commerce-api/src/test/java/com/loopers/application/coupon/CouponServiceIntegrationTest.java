package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
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
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

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
}
