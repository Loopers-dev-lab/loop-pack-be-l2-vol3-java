package com.loopers.domain.user;

import com.loopers.domain.user.exception.UserValidationException;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;

public record User(
        UserId id,
        Password password,
        Name name,
        Email email,
        BirthDate birthDate
) {
    private static final String ERROR_PASSWORD_CONTAINS_BIRTHDATE = "비밀번호에 생년월일을 포함할 수 없습니다";

    public User {
        validatePasswordNotContainsBirthDate(password, birthDate);
    }

    private void validatePasswordNotContainsBirthDate(Password password, BirthDate birthDate) {
        if (password == null || birthDate == null) {
            return;
        }
        if (password.containsDate(birthDate.value())) {
            throw new UserValidationException(ERROR_PASSWORD_CONTAINS_BIRTHDATE);
        }
    }

    public String getMaskedName() {
        String nameValue = name.value();
        if (nameValue.length() <= 1) {
            return "*";
        }
        return nameValue.substring(0, nameValue.length() - 1) + "*";
    }
}
