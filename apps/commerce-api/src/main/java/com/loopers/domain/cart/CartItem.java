package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "option_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private CartItem(Long id, Long userId, Long optionId, int quantity) {
        validateUserId(userId);
        validateOptionId(optionId);
        validateQuantity(quantity);
        this.id = id;
        this.userId = userId;
        this.optionId = optionId;
        this.quantity = quantity;
    }

    public static CartItem create(Long userId, Long optionId, int quantity) {
        return new CartItem(null, userId, optionId, quantity);
    }

    public static CartItem of(Long id, Long userId, Long optionId, int quantity) {
        return new CartItem(id, userId, optionId, quantity);
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 장바구니 항목만 수정할 수 있습니다.");
        }
    }

    public void addQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity += quantity;
    }

    public void updateQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateOptionId(Long optionId) {
        if (optionId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "옵션 ID는 필수입니다.");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}
