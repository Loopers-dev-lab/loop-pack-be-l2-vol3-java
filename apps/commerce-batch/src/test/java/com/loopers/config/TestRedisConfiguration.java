package com.loopers.config;

import com.loopers.config.redis.RedisNodeInfo;
import com.loopers.config.redis.RedisProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class TestRedisConfiguration {

    private static final int REDIS_PORT = 6379;
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.0");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT)
            .withReuse(true);

    static {
        REDIS_CONTAINER.start();
    }

    @Bean
    @Primary
    public RedisProperties testRedisProperties() {
        String host = REDIS_CONTAINER.getHost();
        int port = REDIS_CONTAINER.getMappedPort(REDIS_PORT);

        RedisNodeInfo master = new RedisNodeInfo(host, port);
        List<RedisNodeInfo> replicas = List.of(new RedisNodeInfo(host, port));

        return new RedisProperties(0, master, replicas);
    }
}
