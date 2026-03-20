package com.loopers.fake;

import com.loopers.infrastructure.redis.StockReservationRedisRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StockReservationRedisRepository Fake — AtomicLong 기반.
 * Redis 의존성 없이 재고 DECR/INCR을 테스트한다.
 */
public class FakeStockReservationRedisRepository extends StockReservationRedisRepository {

    private final Map<Long, AtomicLong> store = new ConcurrentHashMap<>();

    public FakeStockReservationRedisRepository() {
        super(null, null);
    }

    @Override
    public Long decrease(Long productId, int quantity) {
        AtomicLong stock = store.computeIfAbsent(productId, k -> new AtomicLong(0));
        return stock.addAndGet(-quantity);
    }

    @Override
    public Long increase(Long productId, int quantity) {
        AtomicLong stock = store.computeIfAbsent(productId, k -> new AtomicLong(0));
        return stock.addAndGet(quantity);
    }

    @Override
    public Long getStock(Long productId) {
        AtomicLong stock = store.get(productId);
        return stock != null ? stock.get() : null;
    }

    @Override
    public void setStock(Long productId, long quantity) {
        store.computeIfAbsent(productId, k -> new AtomicLong(0)).set(quantity);
    }

    public void clear() {
        store.clear();
    }
}
