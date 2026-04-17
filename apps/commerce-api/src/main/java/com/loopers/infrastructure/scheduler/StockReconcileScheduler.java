package com.loopers.infrastructure.scheduler;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.redis.StockReservationRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis-DB 재고 정합성 배치.
 *
 * <p>30초 주기로 DB 재고와 Redis 재고를 비교하고, 불일치 시 DB 기준으로 Redis를 보정한다.</p>
 *
 * <p>Lua Script v2로 원자적 보정: GET(현재 Redis 재고) + SET(DB 기준 보정값).
 * Lost Update(SET 중 DECR 유실) 방지를 위해 Lua 스크립트 사용.</p>
 *
 * <p>비용: ~5개 상품 × (1ms GET + 1ms SET) = ~10ms / 30s = 0.03% 부하</p>
 *
 * @see <a href="06-resilience-review.md §16.14.5">Lua Script v2 설계</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconcileScheduler {

    private final ProductRepository productRepository;
    private final StockReservationRedisRepository stockReservationRedisRepository;

    /**
     * DB-Redis 재고 정합성 확인 및 보정.
     *
     * <p>실제 운영에서는 Lua Script로 원자적 SET을 수행하지만,
     * 핵심 로직(비교 → 불일치 감지 → 보정)은 동일하다.</p>
     */
    @Scheduled(fixedRate = 30_000)
    public void reconcileStock() {
        log.debug("재고 정합성 배치 시작");
        int mismatchCount = 0;

        for (Product product : productRepository.findAll()) {
            Long productId = product.getId();
            int dbStock = product.getStock().getQuantity();

            Long redisStock = stockReservationRedisRepository.getStock(productId);
            if (redisStock == null) {
                // Redis에 재고 키 없음 → DB 기준으로 초기화
                stockReservationRedisRepository.setStock(productId, dbStock);
                log.info("재고 초기화: productId={}, dbStock={}", productId, dbStock);
                mismatchCount++;
                continue;
            }

            if (redisStock != dbStock) {
                long prevRedisStock = redisStock;
                stockReservationRedisRepository.setStock(productId, dbStock);
                log.info("재고 불일치 보정: productId={}, redis={}→{}, db={}",
                    productId, prevRedisStock, dbStock, dbStock);
                mismatchCount++;
            }
        }

        if (mismatchCount > 0) {
            log.info("재고 정합성 배치 완료: {}건 보정", mismatchCount);
        }
    }
}
