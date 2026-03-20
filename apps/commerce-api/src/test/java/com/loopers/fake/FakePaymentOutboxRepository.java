package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.PaymentOutbox;
import com.loopers.domain.payment.PaymentOutboxRepository;
import com.loopers.domain.payment.PaymentOutboxStatus;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakePaymentOutboxRepository implements PaymentOutboxRepository {

    private final Map<Long, PaymentOutbox> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public PaymentOutbox save(PaymentOutbox outbox) {
        if (outbox.getId() == null || outbox.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(outbox, id);
        }
        setCreatedAtIfAbsent(outbox);
        store.put(outbox.getId(), outbox);
        return outbox;
    }

    @Override
    public Optional<PaymentOutbox> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<PaymentOutbox> findAllByStatus(PaymentOutboxStatus status) {
        return store.values().stream()
            .filter(o -> o.getStatus() == status)
            .toList();
    }

    @Override
    public Optional<PaymentOutbox> findByPaymentId(Long paymentId) {
        return store.values().stream()
            .filter(o -> o.getPaymentId().equals(paymentId))
            .findFirst();
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

    private void setCreatedAtIfAbsent(PaymentOutbox outbox) {
        if (outbox.getCreatedAt() == null) {
            try {
                Field createdAtField = BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(outbox, ZonedDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
