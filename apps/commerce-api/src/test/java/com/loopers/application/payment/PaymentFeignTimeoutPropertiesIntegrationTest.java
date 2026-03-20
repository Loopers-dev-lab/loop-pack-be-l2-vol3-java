package com.loopers.application.payment;

import com.loopers.CommerceApiApplication;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1: Feign 기본 타임아웃이 application.yml에 반영되는지 검증 (06 §14 Phase 1).
 */
@SpringBootTest(classes = CommerceApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(MySqlTestContainersConfig.class)
class PaymentFeignTimeoutPropertiesIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("feign default connect/read timeout이 06 계획과 동일하게 바인딩된다.")
    void feignDefaultConfig_shouldBindConnectAndReadTimeouts() {
        // given
        // when
        Integer connect = environment.getProperty("feign.client.config.default.connectTimeout", Integer.class);
        Integer read = environment.getProperty("feign.client.config.default.readTimeout", Integer.class);
        // then
        assertThat(connect).isEqualTo(500);
        assertThat(read).isEqualTo(2000);
    }
}
