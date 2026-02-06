package com.loopers.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.LocalDate;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE) // 생성자를 private으로 제한
public class Birthday {

    LocalDate value;

    public static Birthday of(LocalDate value) {
        if(value == null) {
            throw new IllegalArgumentException("생년월일은 필수 입력값입니다.");
        }
        if(value.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 미래 날짜일 수 없습니다.");
        }
        if(value.isBefore(LocalDate.of(1900, 1,1))) {
            throw new IllegalArgumentException("생년월일은 1900년 이후여야 합니다.");
        }
        return new Birthday(value);
    }
}
