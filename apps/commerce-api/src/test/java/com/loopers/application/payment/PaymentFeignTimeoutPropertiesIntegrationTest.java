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
 * 역할: 운영 설정과 동일하게 Feign connect/read 타임아웃이 Environment에 바인딩되는지 확인한다.
 * WireMock 등으로 실제 지연을 재현하기 전, 숫자(500ms/2s)가 의도대로 로드되는지 빠르게 검증 (06 Phase 1).
 */
@SpringBootTest(classes = CommerceApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(MySqlTestContainersConfig.class)
class PaymentFeignTimeoutPropertiesIntegrationTest {

    @Autowired
    private Environment environment;

    /** default Feign 클라이언트에 connect 500ms, read 2000ms가 적용되는지 검증. */
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
