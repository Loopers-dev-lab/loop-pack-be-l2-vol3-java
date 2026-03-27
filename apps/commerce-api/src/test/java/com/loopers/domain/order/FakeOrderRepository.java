package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class FakeOrderRepository implements OrderRepository {

    private final Map<Long, OrderModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderModel save(OrderModel orderModel) {
        if (orderModel.getId() == 0L) {
            try {
                var idField = orderModel.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(orderModel, idGenerator.getAndIncrement());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        store.put(orderModel.getId(), orderModel);
        return orderModel;
    }

    @Override
    public Optional<OrderModel> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<OrderModel> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return store.values().stream()
            .filter(order -> order.getUserId().equals(userId))
            .filter(order -> {
                ZonedDateTime createdAt = order.getCreatedAt();
                if (createdAt == null) return true;
                return !createdAt.isBefore(startAt) && !createdAt.isAfter(endAt);
            })
            .toList();
    }

    @Override
    public Optional<OrderModel> findByIdWithLock(Long id) {
        return findById(id);
    }

    @Override
    public Page<OrderModel> findAll(Pageable pageable) {
        List<OrderModel> all = new ArrayList<>(store.values());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());

        List<OrderModel> pageContent = start >= all.size()
            ? new ArrayList<>()
            : all.subList(start, end);

        return new PageImpl<>(pageContent, pageable, all.size());
    }

    @Override
    public boolean existsByUserIdAndStatus(Long userId, OrderStatus status) {
        return store.values().stream()
                .anyMatch(order -> order.getUserId().equals(userId)
                        && order.getStatus() == status);
    }
}
