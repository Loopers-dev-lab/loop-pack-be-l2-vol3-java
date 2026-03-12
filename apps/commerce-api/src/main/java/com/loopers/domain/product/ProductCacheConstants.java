package com.loopers.domain.product;

import java.time.Duration;

import com.loopers.domain.shared.cache.CacheKey;
import com.loopers.domain.shared.cache.CacheType;

import lombok.experimental.UtilityClass;

/**
 * 상품 캐시에서 공유하는 키·TTL·타입 상수.
 *
 * @see ProductReader
 * @see ProductWriter
 */
@UtilityClass
final class ProductCacheConstants {

    static final CacheKey LIST_KEY = new CacheKey("product", "list", "v1");
    static final CacheKey DETAIL_KEY = new CacheKey("product", "detail", "v1");

    static final Duration LIST_TTL = Duration.ofMinutes(1);
    static final Duration DETAIL_TTL = Duration.ofMinutes(5);

    static final CacheType<Product> PRODUCT_TYPE = new CacheType<>() {};
}
