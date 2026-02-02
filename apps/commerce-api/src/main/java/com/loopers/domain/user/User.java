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

    public User(String loginId, String password, String name, String birthDate, String email, PasswordEncoder passwordEncoder) {
        this.loginId = new LoginId(loginId);
        this.name = new UserName(name);
        this.birthDate = new BirthDate(birthDate);
        this.email = new Email(email);

        validatePasswordNotContainsBirthDate(password);
        this.password = new Password(password, passwordEncoder);
    }

    public void updatePassword(String oldPassword, String newPassword, PasswordEncoder passwordEncoder) {
        if (!matchesPassword(oldPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호가 일치하지 않습니다.");
        }
        if (matchesPassword(newPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호와 동일한 비밀번호로 수정할 수 없습니다.");
        }
        validatePasswordNotContainsBirthDate(newPassword);
        this.password = new Password(newPassword, passwordEncoder);
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(rawPassword, password.getValue());
    }

    private void validatePasswordNotContainsBirthDate(String rawPassword) {
        if (rawPassword.contains(birthDate.getValueWithoutHyphen())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
