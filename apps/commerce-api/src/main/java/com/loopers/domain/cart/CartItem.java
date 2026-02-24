package com.loopers.domain.cart;

import com.loopers.domain.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected CartItem() {}

    CartItem(Cart cart, Long productId, int quantity) {
        Objects.requireNonNull(cart, "장바구니는 필수입니다.");
        Objects.requireNonNull(productId, "상품 ID는 필수입니다.");
        this.cart = cart;
        this.productId = productId;
        this.quantity = new Quantity(quantity).value();
    }

    public Quantity getQuantity() {
        return new Quantity(quantity);
    }

    public void addQuantity(int amount) {
        this.quantity = getQuantity().add(amount).value();
    }

    public void updateQuantity(int quantity) {
        this.quantity = new Quantity(quantity).value();
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
