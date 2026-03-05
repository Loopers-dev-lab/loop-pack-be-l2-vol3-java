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
        if (userId == null) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
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

    /**
     * 충전 금액 검증 (POJO 빠른 실패)
     * 실제 상태 변경은 원자적 UPDATE(SQL)가 담당한다.
     */
    public void validateCharge(int amount) {
        if (amount <= 0) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
    }

    /**
     * 사용 금액/잔액 검증 (POJO 빠른 실패)
     * 실제 상태 변경은 원자적 UPDATE(SQL)가 담당한다.
     */
    public void validateUse(int amount) {
        if (amount <= 0) {
            throw new CoreException(PointErrorType.INVALID_AMOUNT);
        }
        Money amountMoney = new Money(amount);
        if (!this.balance.isGreaterThanOrEqual(amountMoney)) {
            throw new CoreException(PointErrorType.INSUFFICIENT_BALANCE);
        }
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

    /**
     * 주문 금액 기반 적립 포인트 계산 (static — 원자적 UPDATE 전 계산용)
     */
    public static int calculateEarnedPoints(int orderAmount) {
        int earnRate;
        if (orderAmount >= 100_000) {
            earnRate = 3;
        } else if (orderAmount >= 50_000) {
            earnRate = 2;
        } else {
            earnRate = 1;
        }
        return orderAmount * earnRate / 100;
    }

    /**
     * 주문 금액 기반 포인트 적립
     *
     * 적립률:
     * - 10만원 이상: 3%
     * - 5만원 이상: 2%
     * - 그 외: 1%
     */
    public void earn(int orderAmount) {
        int earnedPoints = calculateEarnedPoints(orderAmount);
        if (earnedPoints > 0) {
            this.balance = this.balance.plus(new Money(earnedPoints));
            this.updatedAt = ZonedDateTime.now();
        }
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
