package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakePaymentRepository implements PaymentRepository {

    private final Map<Long, PaymentModel> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public PaymentModel save(PaymentModel payment) {
        if (payment.getId() == null || payment.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(payment, id);
        }
        setCreatedAtIfAbsent(payment);
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<PaymentModel> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return store.values().stream()
            .filter(p -> p.getOrderId().equals(orderId))
            .findFirst();
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return store.values().stream()
            .filter(p -> transactionKey.equals(p.getTransactionKey()))
            .findFirst();
    }

    @Override
    public List<PaymentModel> findAllByStatus(PaymentStatus status) {
        return store.values().stream()
            .filter(p -> p.getStatus() == status)
            .toList();
    }

    @Override
    public int updateStatusConditionally(Long paymentId, PaymentStatus newStatus,
                                          List<PaymentStatus> allowedCurrentStatuses) {
        PaymentModel payment = store.get(paymentId);
        if (payment == null) return 0;
        if (!allowedCurrentStatuses.contains(payment.getStatus())) return 0;

        try {
            Field statusField = PaymentModel.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(payment, newStatus);
            return 1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private void setCreatedAtIfAbsent(PaymentModel payment) {
        if (payment.getCreatedAt() == null) {
            try {
                Field createdAtField = BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(payment, ZonedDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
