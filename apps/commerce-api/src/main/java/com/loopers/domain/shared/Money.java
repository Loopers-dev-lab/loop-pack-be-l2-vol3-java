package com.loopers.domain.shared;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import jakarta.persistence.Embeddable;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString
public class Money {

    public static final Money ZERO = new Money(0L);

    private Long amount;

    private Money(Long amount) {
        this.amount = amount;
    }

    public static Money wons(Long amount) {
        validate(amount);
        return new Money(amount);
    }

    public static Money wonsOrNull(Long amount) {
        if (amount == null) {
            return null;
        }
        return wons(amount);
    }

    public static <T> Money sum(Collection<T> bags, Function<T, Money> monetary) {
        return bags.stream().map(monetary).reduce(Money.ZERO, Money::plus);
    }

    public Money plus(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money minus(Money other) {
        return wons(this.amount - other.amount);
    }

    public Money multiply(Long multiplier) {
        return new Money(this.amount * multiplier);
    }

    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }

    public static Money min(Money a, Money b) {
        return a.amount <= b.amount ? a : b;
    }

    private static void validate(Long amount) {
        if (Objects.isNull(amount)) {
            throw new CoreException(ErrorType.REQUIRED_MONEY_AMOUNT);
        }
        if (amount < 0) {
            throw new CoreException(ErrorType.INVALID_MONEY_AMOUNT);
        }
    }
}
