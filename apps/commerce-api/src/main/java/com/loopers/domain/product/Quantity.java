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
public class Quantity {

    private int value;

    public Quantity(int value) {
        if (value < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        this.value = value;
    }

    public Quantity add(Quantity other) {
        return new Quantity(this.value + other.value);
    }
}
