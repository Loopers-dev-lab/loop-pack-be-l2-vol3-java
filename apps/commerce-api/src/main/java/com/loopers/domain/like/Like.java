package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Like {
    private final Long id;
    private final Long userId;
    private final Long productId;

    private Like(Long id, Long userId, Long productId) {
        validateUserId(userId);
        validateProductId(productId);
        this.id = id;
        this.userId = userId;
        this.productId = productId;
    }

    public static Like create(Long userId, Long productId) {
        return new Like(null, userId, productId);
    }

    public static Like of(Long id, Long userId, Long productId) {
        return new Like(id, userId, productId);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }
}
