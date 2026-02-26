package com.loopers.domain.member;

import com.loopers.domain.member.exception.MemberValidationException;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.member.vo.Phone;
import com.loopers.domain.member.vo.MemberId;

public record Member(
        MemberId id,
        Password password,
        Name name,
        Email email,
        BirthDate birthDate,
        Phone phone
) {
    private static final String ERROR_PASSWORD_CONTAINS_BIRTHDATE = "비밀번호에 생년월일을 포함할 수 없습니다";

    public Member(MemberId id, Password password, Name name, Email email, Phone phone) {
        this(id, password, name, email, null, phone);
    }

    public Member {
        if (birthDate != null && phone != null) {
            validatePasswordNotContainsBirthDate(password, birthDate);
        }
    }

    private void validatePasswordNotContainsBirthDate(Password password, BirthDate birthDate) {
        if (password == null || birthDate == null) {
            return;
        }
        if (password.isEncoded()) {
            return;
        }
        if (password.containsDate(birthDate.value())) {
            throw new MemberValidationException(ERROR_PASSWORD_CONTAINS_BIRTHDATE);
        }
    }

    public String getMaskedName() {
        String nameValue = name.value();
        if (nameValue.length() <= 1) {
            return "*";
        }
        return nameValue.substring(0, nameValue.length() - 1) + "*";
    }

    public Phone phone() {
        return phone;
    }

    public BirthDate birthDate() {
        return birthDate;
    }
}
