package com.loopers;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("CommerceApi 컨텍스트 테스트")
class CommerceApiContextTest {

    @Test
    @DisplayName("[CommerceApiContextTest] Spring Boot 애플리케이션 컨텍스트 로드 -> 모든 빈이 올바르게 로드됨")
    void contextLoads() {
    }
}
