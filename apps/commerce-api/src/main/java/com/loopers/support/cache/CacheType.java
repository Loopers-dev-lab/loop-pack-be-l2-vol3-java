package com.loopers.support.cache;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CacheType {

    BRAND_LIST(Names.BRAND_LIST, Duration.ofHours(24)),
    PRODUCT_DETAIL(Names.PRODUCT_DETAIL, Duration.ofMinutes(10)),
    PRODUCT_LIST_LATEST(Names.PRODUCT_LIST_LATEST, Duration.ofMinutes(1)),
    PRODUCT_LIST_PRICE(Names.PRODUCT_LIST_PRICE, Duration.ofMinutes(1)),
    PRODUCT_LIST_LIKES(Names.PRODUCT_LIST_LIKES, Duration.ofMinutes(1));

    private final String cacheName;
    private final Duration ttl;

    public static class Names {
        public static final String BRAND_LIST = "brand:list";
        public static final String PRODUCT_DETAIL = "product:detail";
        public static final String PRODUCT_LIST_LATEST = "product:list:latest";
        public static final String PRODUCT_LIST_PRICE = "product:list:price";
        public static final String PRODUCT_LIST_LIKES = "product:list:likes";
    }
}
