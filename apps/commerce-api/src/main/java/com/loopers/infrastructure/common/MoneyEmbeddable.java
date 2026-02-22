package com.loopers.infrastructure.common;

import com.loopers.domain.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyEmbeddable {

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    private MoneyEmbeddable(BigDecimal amount) {
        this.amount = amount;
    }

    public static MoneyEmbeddable from(Money money) {
        return new MoneyEmbeddable(money.getAmount());
    }

    public Money toDomain() {
        return Money.of(amount);
    }
}
