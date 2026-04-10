package com.loopers.application.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랭킹 시스템 설정.
 *
 * <p>Additionals "실시간 Weight 조절"을 위해 {@code @ConfigurationProperties}로 외부화.
 * Spring Cloud Config 또는 yml 변경 + actuator refresh로 런타임 가중치 조정이 가능하다.</p>
 *
 * <pre>
 * ranking:
 *   weights:
 *     view: 0.1
 *     like: 0.2
 *     order: 0.7
 * </pre>
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
    Weights weights,
    double carryOverRate
) {
    public record Weights(double view, double like, double order) {}
}
