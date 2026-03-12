package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.UUID;

public record OrderItem(
        UUID id,
        UUID orderId,
        UUID productId,
        int quantity,
        String snapshotProductName,
        int snapshotPrice,
        String snapshotBrandName
) {

    public OrderItem {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        if (snapshotProductName == null || snapshotProductName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 필수입니다.");
        }
        if (snapshotPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격 스냅샷은 0 이상이어야 합니다.");
        }
        if (snapshotBrandName == null || snapshotBrandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명 스냅샷은 필수입니다.");
        }
    }

    public OrderItem(UUID productId, int quantity, String snapshotProductName, int snapshotPrice, String snapshotBrandName) {
        this(null, null, productId, quantity, snapshotProductName, snapshotPrice, snapshotBrandName);
    }

    public int totalPrice() {
        return snapshotPrice * quantity;
    }
}
