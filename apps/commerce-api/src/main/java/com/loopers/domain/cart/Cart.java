package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected Cart() {}

    public Cart(Long userId) {
        Objects.requireNonNull(userId, "유저 ID는 필수입니다.");
        this.userId = userId;
    }

    public void addItem(Long productId, int quantity) {
        Objects.requireNonNull(productId, "상품 ID는 필수입니다.");

        for (CartItem item : items) {
            if (item.getProductId().equals(productId)) {
                item.addQuantity(quantity);
                return;
            }
        }

        items.add(new CartItem(this, productId, quantity));
    }

    public void removeItem(Long cartItemId) {
        Objects.requireNonNull(cartItemId, "장바구니 항목 ID는 필수입니다.");

        boolean removed = items.removeIf(item -> cartItemId.equals(item.getId()));
        if (!removed) {
            throw new CoreException(ErrorType.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다.");
        }
    }

    public void updateItemQuantity(Long cartItemId, int quantity) {
        Objects.requireNonNull(cartItemId, "장바구니 항목 ID는 필수입니다.");

        CartItem cartItem = items.stream()
            .filter(item -> cartItemId.equals(item.getId()))
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."));

        cartItem.changeQuantity(quantity);
    }

    public void clear() {
        items.clear();
    }

    public void removeUnavailableItems(Set<Long> availableProductIds) {
        items.removeIf(item -> !availableProductIds.contains(item.getProductId()));
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
