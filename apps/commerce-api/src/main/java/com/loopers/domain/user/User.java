package com.loopers.domain.user;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private Password password;

    @Embedded
    private UserName name;

    @Embedded
    private BirthDate birthDate;

    @Embedded
    private Email email;

    public User(String loginId, String password, String name, String birthDate, String email) {
        this.loginId = new LoginId(loginId);
        this.password = new Password(password);
        this.name = new UserName(name);
        this.birthDate = new BirthDate(birthDate);
        this.email = new Email(email);

        validatePasswordNotContainsBirthDate();
    }

    private void validatePasswordNotContainsBirthDate() {
        if (password.contains(birthDate.getValueWithoutHyphen())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
