package com.loopers.domain.payment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<Long, Payment> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == 0L) {
            try {
                var idField = payment.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(payment, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findByPgOrderCode(String pgOrderCode) {
        return store.values().stream()
                    .filter(p -> p.getPgOrderCode().equals(pgOrderCode))
                    .findFirst();
    }

    @Override
    public Optional<Payment> findByPgTransactionKey(String pgTransactionId) {
        return store.values().stream()
                    .filter(p -> pgTransactionId.equals(p.getPgTransactionKey()))
                    .findFirst();
    }

    @Override
    public List<Payment> findAllByStatus(PaymentStatus status) {
        return store.values().stream()
                    .filter(p -> p.getStatus() == status)
                    .toList();
    }

    @Override
    public int completeIfPending(Long id) {
        return findById(id)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .map(p -> {
                    try {
                        var statusField = Payment.class.getDeclaredField("status");
                        statusField.setAccessible(true);
                        statusField.set(p, PaymentStatus.COMPLETED);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return 1;
                })
                .orElse(0);
    }

    @Override
    public int failIfPending(Long id, String reason) {
        return findById(id)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .map(p -> {
                    try {
                        var statusField = Payment.class.getDeclaredField("status");
                        statusField.setAccessible(true);
                        statusField.set(p, PaymentStatus.FAILED);
                        var reasonField = Payment.class.getDeclaredField("failReason");
                        reasonField.setAccessible(true);
                        reasonField.set(p, reason);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return 1;
                })
                .orElse(0);
    }
}
