package com.loopers.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(value = "datasource.redis")
public record RedisProperties(
        String mode,
        int database,
        RedisNodeInfo master,
        List<RedisNodeInfo> replicas,
        List<RedisNodeInfo> clusterNodes
) {
    public boolean isCluster() {
        return "cluster".equalsIgnoreCase(mode);
    }
}
