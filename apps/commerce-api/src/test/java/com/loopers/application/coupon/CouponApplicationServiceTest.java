package com.loopers.application.coupon;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Coupon Application Service 통합 테스트")
class CouponApplicationServiceTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("CouponApplicationService 빈이 등록되어야 한다")
    void couponApplicationServiceBeanShouldExist() {
        try {
            Class<?> appServiceClass = Class.forName("com.loopers.application.coupon.CouponApplicationService");
            Object bean = applicationContext.getBean(appServiceClass);
            assertThat(bean).isNotNull();
        } catch (ClassNotFoundException e) {
            fail("RED: CouponApplicationService 클래스가 아직 구현되지 않았습니다.");
        }
    }
}
