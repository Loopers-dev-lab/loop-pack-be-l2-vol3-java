package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * 사용자 포인트 도메인 엔티티.
 */
public class UserPoint {

    private Long id;
    private Long userId;
    private long balance;
    private ZonedDateTime updatedAt;

    private UserPoint() {}

    /**
     * 새 포인트 생성.
     *
     * @param userId 사용자 ID
     * @param initialBalance 초기 잔액
     * @return 생성된 UserPoint
     * @throws CoreException 초기 잔액이 음수인 경우
     */
    public static UserPoint create(Long userId, long initialBalance) {
        if (initialBalance < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "잔액은 0 이상이어야 합니다.");
        }
        UserPoint point = new UserPoint();
        point.userId = userId;
        point.balance = initialBalance;
        point.updatedAt = ZonedDateTime.now();
        return point;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static UserPoint reconstitute(Long id, Long userId, long balance, ZonedDateTime updatedAt) {
        UserPoint point = new UserPoint();
        point.id = id;
        point.userId = userId;
        point.balance = balance;
        point.updatedAt = updatedAt;
        return point;
    }

    /**
     * 포인트 충전.
     *
     * @param amount 충전 금액
     * @throws CoreException 충전 금액이 0 이하인 경우
     */
    public void charge(long amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "충전 금액은 1 이상이어야 합니다.");
        }
        this.balance += amount;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 포인트 사용.
     *
     * @param amount 사용 금액
     * @throws CoreException 사용 금액이 0 이하이거나 잔액 부족인 경우
     */
    public void use(long amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 금액은 1 이상이어야 합니다.");
        }
        if (!hasEnough(amount)) {
            throw new CoreException(ErrorType.INSUFFICIENT_POINT);
        }
        this.balance -= amount;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 잔액 충분 여부 확인.
     *
     * @param amount 확인할 금액
     * @return 충분 여부
     */
    public boolean hasEnough(long amount) {
        return balance >= amount;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
