package com.loopers;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class CommerceApiContextTest {

    @Test
    void contextLoads() {
        // 이 테스트는 Spring Boot 애플리케이션 컨텍스트가 로드되는지 확인합니다.
        // 모든 빈이 올바르게 로드되었는지 확인하는 데 사용됩니다.
    }
}
