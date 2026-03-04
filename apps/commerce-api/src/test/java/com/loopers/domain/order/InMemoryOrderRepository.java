package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        if (order.getId() == 0L) {
            try {
                var idField = order.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(order, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return store.values().stream()
                    .filter(o -> o.getUserId().equals(userId))
                    .filter(o -> o.getCreatedAt() != null)
                    .filter(o -> !o.getCreatedAt().isBefore(startAt) && !o.getCreatedAt().isAfter(endAt))
                    .sorted(Comparator.comparing(Order::getId).reversed())
                    .toList();
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        List<Order> list = store.values().stream()
                                .sorted(Comparator.comparing(Order::getId).reversed())
                                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<Order> content = start >= list.size() ? List.of() : list.subList(start, end);

        return new PageImpl<>(content, pageable, list.size());
    }
}
