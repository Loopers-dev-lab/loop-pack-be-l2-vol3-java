package com.loopers.fake;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake OrderRepository.
 * Map 기반 in-memory 구현.
 */
public class FakeOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final AtomicLong itemIdGenerator = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        Long orderId = order.getId();
        if (orderId == null) {
            orderId = idGenerator.getAndIncrement();
            // OrderItem에도 ID 부여
            List<OrderItem> itemsWithIds = order.getItems().stream()
                .map(item -> OrderItem.reconstitute(
                    itemIdGenerator.getAndIncrement(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getPriceSnapshot()
                ))
                .toList();

            order = Order.reconstitute(
                orderId,
                order.getUserId(),
                itemsWithIds,
                order.getTotalPrice(),
                order.getStatus(),
                order.getCreatedAt()
            );
        }
        store.put(orderId, order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Order> findByIdAndUserId(Long id, Long userId) {
        return findById(id)
            .filter(order -> order.getUserId().equals(userId));
    }

    @Override
    public List<Order> findAllByUserId(Long userId, int offset, int limit) {
        return store.values().stream()
            .filter(order -> order.getUserId().equals(userId))
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .skip(offset)
            .limit(limit)
            .toList();
    }

    @Override
    public long countByUserId(Long userId) {
        return store.values().stream()
            .filter(order -> order.getUserId().equals(userId))
            .count();
    }

    /**
     * 테스트용: 저장소 초기화
     */
    public void clear() {
        store.clear();
        idGenerator.set(1);
        itemIdGenerator.set(1);
    }

    /**
     * 테스트용: 저장된 주문 수 조회
     */
    public int size() {
        return store.size();
    }
}
