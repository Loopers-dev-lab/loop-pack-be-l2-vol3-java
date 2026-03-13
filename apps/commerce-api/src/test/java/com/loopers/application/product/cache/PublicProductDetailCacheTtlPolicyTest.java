package com.loopers.application.product.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PublicProductDetailCacheTtlPolicyTest {

    @Test
    @DisplayName("jitter가 비활성화면 기본 TTL을 그대로 사용한다")
    void resolve_withoutJitter_returnsBaseTtl() {
        PublicProductDetailCacheTtlPolicy policy = new PublicProductDetailCacheTtlPolicy(
                new PublicProductDetailCacheProperties(true, Duration.ofSeconds(30), false, 0.1)
        );

        assertThat(policy.resolve()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("jitter는 지정한 비율 범위 안에서 TTL을 분산한다")
    void jitter_withinExpectedRange() {
        PublicProductDetailCacheTtlPolicy policy = new PublicProductDetailCacheTtlPolicy(
                new PublicProductDetailCacheProperties(true, Duration.ofSeconds(30), true, 0.1)
        );

        Duration min = policy.jitter(Duration.ofSeconds(30), 0.1, 0.0);
        Duration mid = policy.jitter(Duration.ofSeconds(30), 0.1, 0.5);
        Duration max = policy.jitter(Duration.ofSeconds(30), 0.1, 1.0);

        assertThat(min).isEqualTo(Duration.ofSeconds(27));
        assertThat(mid).isEqualTo(Duration.ofSeconds(30));
        assertThat(max).isEqualTo(Duration.ofSeconds(33));
    }
}
