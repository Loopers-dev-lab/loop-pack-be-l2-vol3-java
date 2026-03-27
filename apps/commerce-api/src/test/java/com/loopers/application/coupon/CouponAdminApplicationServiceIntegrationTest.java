package com.loopers.application.coupon;

import com.loopers.application.coupon.command.CreateCouponCommand;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class CouponAdminApplicationServiceIntegrationTest {

    @Autowired
    private CouponAdminApplicationService couponAdminApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("CouponAdminApplicationService 통합: 생성 후 목록/상세 조회 가능")
    void createListFind() {
        Coupon created = couponAdminApplicationService.create(new CreateCouponCommand(
                "쿠폰통합",
                CouponType.FIXED,
                3000,
                10000,
                100,
                LocalDateTime.now().plusDays(5)
        ));

        var page = couponAdminApplicationService.list(PageRequest.of(0, 20));
        Coupon found = couponAdminApplicationService.findById(created.id());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(found.id()).isEqualTo(created.id());
    }
}
