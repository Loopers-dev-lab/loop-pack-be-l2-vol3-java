package com.loopers;

import com.loopers.batch.job.demo.DemoJobConfig;
import com.loopers.config.TestRedisConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@TestPropertySource(properties = "spring.batch.job.name=" + DemoJobConfig.JOB_NAME)
public class CommerceBatchApplicationTest {
    @Test
    void contextLoads() {}
}
