package com.loopers.infrastructure.payment.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.model.PaymentProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_payment_products")
@SQLRestriction("deleted_at IS NULL")
public class PaymentProductEntity extends BaseEntity {

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int productPrice;

    @Column(nullable = false)
    private int quantity;

    private PaymentProductEntity(Long paymentId, Long productId, String productName, int productPrice, int quantity) {
        this.paymentId = paymentId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public static PaymentProductEntity toEntity(PaymentProduct pp) {
        return new PaymentProductEntity(
                pp.getPaymentId(),
                pp.getProductId(),
                pp.getProductName(),
                pp.getProductPrice(),
                pp.getQuantity()
        );
    }

    public PaymentProduct toModel() {
        return PaymentProduct.reconstruct(
                this.getId(),
                this.paymentId,
                this.productId,
                this.productName,
                this.productPrice,
                this.quantity
        );
    }
}
