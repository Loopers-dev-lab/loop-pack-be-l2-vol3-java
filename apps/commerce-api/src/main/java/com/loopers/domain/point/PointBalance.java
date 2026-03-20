package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PointBalance(
        UUID id,
        String memberId,
        int balance,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
) {
    public PointBalance {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (balance < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트 잔액은 0 이상이어야 합니다.");
        }
    }

    public PointBalance(String memberId, int balance) {
        this(null, memberId, balance, ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    public PointBalance use(int amount) {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 포인트는 0 이상이어야 합니다.");
        }
        if (amount == 0) {
            return this;
        }
        if (balance < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다.");
        }

        return new PointBalance(id, memberId, balance - amount, createdAt, ZonedDateTime.now(), deletedAt);
    }

    public PointBalance restore(int amount) {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복구 포인트는 0 이상이어야 합니다.");
        }
        if (amount == 0) {
            return this;
        }

        return new PointBalance(id, memberId, balance + amount, createdAt, ZonedDateTime.now(), deletedAt);
    }
}
