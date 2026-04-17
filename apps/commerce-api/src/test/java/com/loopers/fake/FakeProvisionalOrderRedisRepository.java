package com.loopers.fake;

import com.loopers.infrastructure.redis.ProvisionalOrderRedisRepository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProvisionalOrderRedisRepository Fake — ConcurrentHashMap 기반.
 * Redis 의존성 없이 가주문 CRUD를 테스트한다.
 */
public class FakeProvisionalOrderRedisRepository extends ProvisionalOrderRedisRepository {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final Map<Long, Long> ttlStore = new ConcurrentHashMap<>();

    public FakeProvisionalOrderRedisRepository() {
        super(null, null, null);
    }

    @Override
    public void save(Long orderId, Map<String, Object> orderData) {
        store.put(orderId, orderData);
    }

    @Override
    public Optional<Map<String, Object>> findByOrderId(Long orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public void deleteByOrderId(Long orderId) {
        store.remove(orderId);
    }

    @Override
    public boolean exists(Long orderId) {
        return store.containsKey(orderId);
    }

    @Override
    public Set<Long> getAllOrderIds() {
        return Set.copyOf(store.keySet());
    }

    @Override
    public long getTtlSeconds(Long orderId) {
        return store.containsKey(orderId) ? ttlStore.getOrDefault(orderId, 1800L) : -2;
    }

    /**
     * 테스트용 — 특정 가주문의 TTL을 설정한다.
     */
    public void setTtl(Long orderId, long ttlSeconds) {
        ttlStore.put(orderId, ttlSeconds);
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
        ttlStore.clear();
    }
}
