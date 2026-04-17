package com.loopers.domain.product.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Price {

    @Column(name = "price", nullable = false)
    private int value;

    public Price(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
        this.value = value;
    }
}
