package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentMapper paymentMapper;

    public PaymentRepositoryImpl(PaymentJpaRepository paymentJpaRepository, PaymentMapper paymentMapper) {
        this.paymentJpaRepository = paymentJpaRepository;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = paymentMapper.toEntity(payment);
        PaymentEntity saved = paymentJpaRepository.save(entity);
        return paymentMapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id)
                .map(paymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return paymentJpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(paymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId)
                .map(paymentMapper::toDomain);
    }

    @Override
    public List<Payment> findAllByStatusAndRequestedBefore(PaymentStatus status, java.time.ZonedDateTime before, int limit) {
        return paymentJpaRepository.findAllByStatusAndRequestedAtBefore(status, before, PageRequest.of(0, limit))
                .stream()
                .map(paymentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Payment> findAllByStatusAndFailedBefore(PaymentStatus status, java.time.ZonedDateTime before, int limit) {
        return paymentJpaRepository.findAllByStatusAndFailedAtBefore(status, before, PageRequest.of(0, limit))
                .stream()
                .map(paymentMapper::toDomain)
                .toList();
    }
}
