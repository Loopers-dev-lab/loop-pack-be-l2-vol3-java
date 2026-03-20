package com.loopers.domain.payment.repository;

import com.loopers.domain.payment.model.PaymentProduct;

import java.util.List;

public interface PaymentProductRepository {

    List<PaymentProduct> saveAll(List<PaymentProduct> paymentProducts);

    List<PaymentProduct> findByPaymentId(Long paymentId);
}
