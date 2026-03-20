package com.loopers.domain.payment.model;

import lombok.Getter;

@Getter
public class PaymentProduct {

    private Long id;
    private Long paymentId;
    private Long productId;
    private String productName;
    private int productPrice;
    private int quantity;

    private PaymentProduct(Long paymentId, Long productId, String productName, int productPrice, int quantity) {
        this.paymentId = paymentId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public static PaymentProduct create(Long paymentId, Long productId, String productName, int productPrice, int quantity) {
        return new PaymentProduct(paymentId, productId, productName, productPrice, quantity);
    }

    public static PaymentProduct reconstruct(Long id, Long paymentId, Long productId, String productName, int productPrice, int quantity) {
        PaymentProduct pp = new PaymentProduct(paymentId, productId, productName, productPrice, quantity);
        pp.id = id;
        return pp;
    }
}
