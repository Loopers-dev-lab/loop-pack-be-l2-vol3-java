package com.loopers.domain.order;

import com.loopers.domain.PageResult;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class FakeOrderRepository implements OrderRepository {

    private final List<Order> store = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        setId(order, idGenerator.getAndIncrement());
        store.add(order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return store.stream().filter(o -> o.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<Order> findByIdWithItems(Long id) {
        return findById(id);
    }

    @Override
    public PageResult<Order> findByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, int page, int size) {
        List<Order> filtered = store.stream()
            .filter(o -> o.getUserId().equals(userId))
            .filter(o -> o.getCreatedAt() != null
                && !o.getCreatedAt().isBefore(startAt)
                && o.getCreatedAt().isBefore(endAt))
            .toList();
        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Order> paged = filtered.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(paged, page, size, total, totalPages);
    }

    @Override
    public PageResult<Order> findAll(int page, int size) {
        int total = store.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Order> paged = store.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(paged, page, size, total, totalPages);
    }

    private void setId(Order order, long id) {
        try {
            Field idField = order.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Order id", e);
        }
    }
}
