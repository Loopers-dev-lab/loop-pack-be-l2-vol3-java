package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 금액을 나타내는 Value Object.
 * 불변 객체로 모든 연산은 새로운 Money 인스턴스를 반환한다.
 */
public record Money(long amount) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
    }

    /**
     * 두 금액을 더한다.
     *
     * @param other 더할 금액
     * @return 합산된 금액
     */
    public Money add(Money other) {
        return new Money(this.amount + other.amount);
    }

    /**
     * 금액에 수량을 곱한다.
     *
     * @param quantity 곱할 수량 (1 이상)
     * @return 곱해진 금액
     * @throws CoreException 수량이 0 이하인 경우
     */
    public Money multiply(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        return new Money(this.amount * quantity);
    }
}