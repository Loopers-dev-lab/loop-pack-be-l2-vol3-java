package com.loopers.domain.product;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import com.loopers.domain.shared.cache.CacheKey;
import com.loopers.domain.shared.cache.CacheType;

import lombok.experimental.UtilityClass;

/**
 * 상품 캐시에서 공유하는 키·TTL·타입 상수.
 *
 * <p>TTL 메서드는 호출마다 ±10% 범위의 jitter를 적용하여
 * 동시 만료로 인한 thundering herd를 방지한다.</p>
 *
 * @see ProductReader
 * @see ProductWriter
 */
@UtilityClass
final class ProductCacheConstants {

    static final CacheKey LIST_KEY = new CacheKey("product", "list", "v1");
    static final CacheKey DETAIL_KEY = new CacheKey("product", "detail", "v1");

    private static final Duration LIST_BASE_TTL = Duration.ofMinutes(1);
    private static final Duration DETAIL_BASE_TTL = Duration.ofMinutes(5);
    private static final double JITTER_RATIO = 0.1;

    static final CacheType<Product> PRODUCT_TYPE = new CacheType<>() {};

    static Duration listTtl() {
        return applyJitter(LIST_BASE_TTL);
    }

    static Duration detailTtl() {
        return applyJitter(DETAIL_BASE_TTL);
    }

    /**
     * 기준 TTL에 ±{@link #JITTER_RATIO} 범위의 랜덤 오프셋을 더해 반환한다.
     * 동일 시점에 캐싱된 키들의 만료 시점을 분산시켜 thundering herd를 방지한다.
     *
     * @param base 기준 TTL
     * @return jitter가 적용된 TTL (최소 1초 보장)
     */
    private static Duration applyJitter(Duration base) {
        long baseSeconds = base.getSeconds();
        long jitterBound = Math.max(1, (long) (baseSeconds * JITTER_RATIO));
        long offset = ThreadLocalRandom.current().nextLong(-jitterBound, jitterBound + 1);
        return Duration.ofSeconds(Math.max(1, baseSeconds + offset));
    }
}
