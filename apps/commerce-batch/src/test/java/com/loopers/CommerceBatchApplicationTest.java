package com.loopers;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.enabled=false")
public class CommerceBatchApplicationTest {
    @Test
    void contextLoads() {}
}
