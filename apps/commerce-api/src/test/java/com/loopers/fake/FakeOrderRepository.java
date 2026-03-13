package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public Order save(Order order) {
        if (order.getId() == null || order.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(order, id);
        }
        setCreatedAtIfAbsent(order);
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findAllByMemberId(Long memberId) {
        return store.values().stream()
                .filter(order -> order.getMemberId().equals(memberId))
                .toList();
    }

    @Override
    public List<Order> findAllByMemberIdAndCreatedAtBetween(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return store.values().stream()
                .filter(order -> order.getMemberId().equals(memberId))
                .filter(order -> {
                    ZonedDateTime createdAt = order.getCreatedAt();
                    return createdAt != null
                            && !createdAt.isBefore(startAt)
                            && !createdAt.isAfter(endAt);
                })
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }

    private void setBaseEntityId(Object entity, long id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCreatedAtIfAbsent(Order order) {
        if (order.getCreatedAt() == null) {
            try {
                Field createdAtField = BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(order, ZonedDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
