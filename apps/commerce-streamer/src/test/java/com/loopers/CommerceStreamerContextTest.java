package com.loopers;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CommerceStreamer 컨텍스트 로드 테스트")
class CommerceStreamerContextTest {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 로드된다")
    void contextLoads() {
        // Spring ApplicationContext 로드 성공 확인
    }
}
