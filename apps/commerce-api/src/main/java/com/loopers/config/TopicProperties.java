package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 토픽 설정 바인딩
 *
 * 환경별 토픽 설정을 외부화:
 * - application-local.yml: replicas=1, min-insync=1
 * - application-prd.yml: replicas=3, min-insync=2
 *
 * 애플리케이션 코드가 인프라 관심사(브로커 클러스터 구성)를 알 필요 없음
 */
@ConfigurationProperties(prefix = "kafka.topic")
public record TopicProperties(
        TopicConfig catalogEvents,
        TopicConfig orderEvents,
        TopicConfig couponIssueRequests,
        TopicConfig userActivityEvents,
        TopicConfig pipelineDlq
) {
    public record TopicConfig(
            String name,
            int partitions,
            int replicas,
            int minInsyncReplicas,
            Long retentionMs
    ) {
        public TopicConfig {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Topic name must not be blank");
            }
            if (partitions <= 0) {
                throw new IllegalArgumentException("Partitions must be positive");
            }
            if (replicas <= 0) {
                throw new IllegalArgumentException("Replicas must be positive");
            }
            if (minInsyncReplicas <= 0 || minInsyncReplicas > replicas) {
                throw new IllegalArgumentException(
                    "min-insync-replicas must be positive and <= replicas"
                );
            }
        }
    }
}
