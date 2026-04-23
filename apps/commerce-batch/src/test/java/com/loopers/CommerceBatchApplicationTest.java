package com.loopers;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
public class CommerceBatchApplicationTest {
    @Test
    void contextLoads() {}
}
