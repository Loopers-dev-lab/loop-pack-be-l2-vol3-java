package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record OrderLine(Long productId, int quantity, long unitPrice) {

    public OrderLine {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        if (unitPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "단가는 0 이상이어야 합니다.");
        }
    }

    public long getTotalPrice() {
        return (long) quantity * unitPrice;
    }
}
