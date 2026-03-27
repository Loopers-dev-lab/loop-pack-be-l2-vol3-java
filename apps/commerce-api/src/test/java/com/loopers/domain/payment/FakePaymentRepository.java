package com.loopers.domain.payment;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakePaymentRepository implements PaymentRepository {

    private final Map<Long, PaymentModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public PaymentModel save(PaymentModel payment) {
        if (payment.getId() == null || payment.getId() == 0L) {
            setId(payment, idGenerator.getAndIncrement());
        }
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
    public Optional<PaymentModel> findByPgTransactionId(String pgTransactionId) {
        return store.values().stream()
                .filter(p -> pgTransactionId.equals(p.getPgTransactionId()))
                .findFirst();
    }

    private void setId(PaymentModel payment, Long id) {
        try {
            Field field = payment.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
