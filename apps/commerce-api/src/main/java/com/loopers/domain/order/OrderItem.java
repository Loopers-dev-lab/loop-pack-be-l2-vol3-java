package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class OrderItem {
    private final Long optionId;
    private final String productName;
    private final String optionName;
    private final Money price;
    private final int quantity;

    private OrderItem(Long optionId, String productName, String optionName, Money price, int quantity) {
        validate(optionId, productName, optionName, price, quantity);
        this.optionId = optionId;
        this.productName = productName;
        this.optionName = optionName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderItem of(Long optionId, String productName, String optionName, Money price, int quantity) {
        return new OrderItem(optionId, productName, optionName, price, quantity);
    }

    public Money getTotalPrice() {
        return price.multiply(quantity);
    }

    private void validate(Long optionId, String productName, String optionName, Money price, int quantity) {
        if (optionId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "옵션 ID는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
        if (optionName == null || optionName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "옵션명은 필수입니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}
