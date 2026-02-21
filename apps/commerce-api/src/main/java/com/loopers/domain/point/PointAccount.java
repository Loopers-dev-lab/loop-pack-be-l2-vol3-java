package com.loopers.domain.point;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.PointErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_accounts")
public class PointAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "balance", nullable = false))
    private Money balance;

    protected PointAccount() {}

    private PointAccount(Long userId) {
        this.userId = userId;
        this.balance = Money.zero();
    }

    public static PointAccount create(Long userId) {
        return new PointAccount(userId);
    }

    public void charge(int amount) {
        if (amount <= 0) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
        this.balance = this.balance.plus(new Money(amount));
    }

    public void use(int amount) {
        if (amount <= 0) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
        Money amountMoney = new Money(amount);
        if (!this.balance.isGreaterThanOrEqual(amountMoney)) {
            throw new CoreException(PointErrorType.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.minus(amountMoney);
    }

    public void refund(int amount) {
        if (amount <= 0) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
        this.balance = this.balance.plus(new Money(amount));
    }

    public Long getUserId() {
        return this.userId;
    }

    public int getBalance() {
        return this.balance.toInt();
    }
}
