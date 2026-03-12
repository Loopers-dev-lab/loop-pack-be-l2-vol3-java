package com.loopers.domain.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {
    private BigDecimal amount;

    @JsonCreator
    private Money(@JsonProperty("amount") BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
        this.amount = amount;
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "차감 결과가 음수입니다: " + this.amount + " - " + other.amount + " = " + result);
        }
        return new Money(result);
    }

    public Money percentage(int rate) {
        return percentage(BigDecimal.valueOf(rate));
    }

    public Money percentage(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인율은 0~100 사이여야 합니다.");
        }
        return new Money(this.amount.multiply(rate).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN));
    }

    public Money min(Money other) {
        return this.amount.compareTo(other.amount) <= 0 ? this : other;
    }
}
