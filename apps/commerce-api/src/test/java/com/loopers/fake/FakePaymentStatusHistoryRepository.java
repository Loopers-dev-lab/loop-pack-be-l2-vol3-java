package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.PaymentStatusHistory;
import com.loopers.domain.payment.PaymentStatusHistoryRepository;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakePaymentStatusHistoryRepository implements PaymentStatusHistoryRepository {

    private final Map<Long, PaymentStatusHistory> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public PaymentStatusHistory save(PaymentStatusHistory history) {
        if (history.getId() == null || history.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(history, id);
        }
        setCreatedAtIfAbsent(history);
        store.put(history.getId(), history);
        return history;
    }

    @Override
    public List<PaymentStatusHistory> findAllByPaymentId(Long paymentId) {
        return store.values().stream()
            .filter(h -> h.getPaymentId().equals(paymentId))
            .toList();
    }

    public List<PaymentStatusHistory> findAll() {
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

    private void setCreatedAtIfAbsent(Object entity) {
        try {
            Field createdAtField = BaseEntity.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            if (createdAtField.get(entity) == null) {
                createdAtField.set(entity, ZonedDateTime.now());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
