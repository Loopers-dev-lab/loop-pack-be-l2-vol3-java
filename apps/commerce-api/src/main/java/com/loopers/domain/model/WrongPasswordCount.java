package com.loopers.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WrongPasswordCount {

    private final int value;

    public static WrongPasswordCount init() {
        return new WrongPasswordCount(0);
    }

    public static WrongPasswordCount of(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("비밀번호 오류 횟수는 음수일 수 없습니다.");
        }
        return new WrongPasswordCount(value);
    }

    public int getValue() {
        return value;
    }

    public WrongPasswordCount increment() {
        return new WrongPasswordCount(this.value + 1);
    }

    public WrongPasswordCount reset() {
        return new WrongPasswordCount(0);
    }

    public boolean isLocked() {
        return this.value >= 5;
    }
}
