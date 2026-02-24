package com.loopers.domain.point;

import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.PointErrorType;
import java.time.ZonedDateTime;

/**
 * 포인트 계좌 - 순수 POJO
 */
public class PointAccount {

    private Long id;
    private Long userId;
    private Money balance;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected PointAccount() {}

    private PointAccount(Long userId) {
        this.userId = userId;
        this.balance = Money.zero();
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static PointAccount reconstitute(
        Long id,
        Long userId,
        int balance,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        PointAccount pointAccount = new PointAccount();
        pointAccount.id = id;
        pointAccount.userId = userId;
        pointAccount.balance = new Money(balance);
        pointAccount.createdAt = createdAt;
        pointAccount.updatedAt = updatedAt;
        pointAccount.deletedAt = deletedAt;
        return pointAccount;
    }

    public static PointAccount create(Long userId) {
        PointAccount pointAccount = new PointAccount(userId);
        ZonedDateTime now = ZonedDateTime.now();
        pointAccount.createdAt = now;
        pointAccount.updatedAt = now;
        return pointAccount;
    }

    public void charge(int amount) {
        if (amount <= 0) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
        this.balance = this.balance.plus(new Money(amount));
        this.updatedAt = ZonedDateTime.now();
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
        this.updatedAt = ZonedDateTime.now();
    }

    public void refund(int amount) {
        if (amount <= 0) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
        this.balance = this.balance.plus(new Money(amount));
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return this.id;
    }

    public Long getUserId() {
        return this.userId;
    }

    public int getBalance() {
        return this.balance.toInt();
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}
