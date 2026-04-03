package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class RedisTestContainersConfig {

    @Container
    @SuppressWarnings("resource")
    private static final RedisContainer REDIS_CONTAINER = new RedisContainer(DockerImageName.parse("redis:8.0"));

    static {
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void overrideRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.redis.database", () -> "0");
        registry.add("datasource.redis.master.host", REDIS_CONTAINER::getHost);
        registry.add("datasource.redis.master.port", REDIS_CONTAINER::getRedisPort);
        registry.add("datasource.redis.replicas[0].host", REDIS_CONTAINER::getHost);
        registry.add("datasource.redis.replicas[0].port", REDIS_CONTAINER::getRedisPort);
    }
}
