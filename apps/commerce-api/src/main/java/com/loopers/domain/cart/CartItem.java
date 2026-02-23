package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class CartItem {
    private final Long id;
    private final Long userId;
    private final Long optionId;
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
