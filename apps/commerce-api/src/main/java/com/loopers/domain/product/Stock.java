package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Stock {

    private int quantity;

    public Stock(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.quantity = quantity;
    }

    public Stock decrease(Quantity quantity) {
        int newQuantity = this.quantity - quantity.getValue();
        if (newQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        return new Stock(newQuantity);
    }

    public Stock increase(Quantity quantity) {
        return new Stock(this.quantity + quantity.getValue());
    }

    public boolean hasEnough(Quantity quantity) {
        return this.quantity >= quantity.getValue();
    }
}
