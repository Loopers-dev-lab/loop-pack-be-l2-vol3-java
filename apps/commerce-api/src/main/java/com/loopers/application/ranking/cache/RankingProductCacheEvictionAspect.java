package com.loopers.application.ranking.cache;

import com.loopers.application.ranking.RankingProductCacheApplicationService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class RankingProductCacheEvictionAspect {

    private final RankingProductCacheApplicationService rankingProductCacheApplicationService;

    @AfterReturning("@annotation(com.loopers.application.ranking.cache.EvictRankingProductCache) && args(productId,..)")
    public void evict(UUID productId) {
        rankingProductCacheApplicationService.evict(productId);
    }
}
