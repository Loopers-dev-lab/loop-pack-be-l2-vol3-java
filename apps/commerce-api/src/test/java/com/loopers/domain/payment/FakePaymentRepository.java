package com.loopers.domain.payment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakePaymentRepository implements PaymentRepository {

    private final Map<Long, Payment> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Payment save(Payment payment) {
        Long fakeId;
        if (!store.containsValue(payment)) {
            fakeId = idGenerator.getAndIncrement();
            store.put(fakeId, payment);
        }
        return payment;
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return store.values().stream()
            .filter(p -> transactionKey.equals(p.getTransactionKey()))
            .findFirst();
    }

    @Override
    public Optional<Payment> findByTransactionKeyForUpdate(String transactionKey) {
        return findByTransactionKey(transactionKey);
    }

    @Override
    public List<Payment> findByOrderId(Long orderId) {
        return store.values().stream()
            .filter(p -> p.getOrderId().equals(orderId))
            .toList();
    }

    @Override
    public List<Payment> findByStatusIn(List<PaymentStatus> statuses) {
        return store.values().stream()
            .filter(p -> statuses.contains(p.getStatus()))
            .toList();
    }

    @Override
    public List<Payment> findPendingByOrderIdAndUserIdForUpdate(Long orderId, Long userId) {
        return store.values().stream()
            .filter(p -> p.getOrderId().equals(orderId)
                && p.getUserId().equals(userId)
                && p.getStatus() == PaymentStatus.PENDING)
            .toList();
    }
}
