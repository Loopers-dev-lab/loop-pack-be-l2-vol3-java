package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * 테스트용 Redis Testcontainers 설정
 *
 * Testcontainers로 단일 Redis 인스턴스를 시작하고,
 * master와 replica를 동일한 주소로 설정한다.
 *
 * RedisConfig에서 master==replica일 때 Standalone으로 fallback하므로,
 * Lettuce StaticMasterReplicaTopologyProvider 오류가 발생하지 않는다.
 */
@Configuration
public class RedisTestContainersConfig {
    private static final RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine").asCompatibleSubstituteFor("redis"))
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        redisContainer.start();

        String host = redisContainer.getHost();
        String port = String.valueOf(redisContainer.getFirstMappedPort());

        System.setProperty("datasource.redis.database", "0");
        System.setProperty("datasource.redis.master.host", host);
        System.setProperty("datasource.redis.master.port", port);
        System.setProperty("datasource.redis.replicas[0].host", host);
        System.setProperty("datasource.redis.replicas[0].port", port);
    }
}
