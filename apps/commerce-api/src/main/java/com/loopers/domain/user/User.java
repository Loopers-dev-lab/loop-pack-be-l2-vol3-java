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

    public static User signUp(String loginId, String password, String name, String birthDate, String email, PasswordEncoder passwordEncoder) {
        User user = new User();

        user.loginId = new LoginId(loginId);
        user.name = new UserName(name);
        user.birthDate = new BirthDate(birthDate);
        user.email = new Email(email);

        user.validatePasswordNotContainsBirthDate(password);
        user.password = new Password(password, passwordEncoder);

        return user;
    }

    public void updatePassword(String oldPassword, String newPassword, PasswordEncoder passwordEncoder) {
        if (!matchesPassword(oldPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }
        if (matchesPassword(newPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.PASSWORD_REUSE_NOT_ALLOWED);
        }
        validatePasswordNotContainsBirthDate(newPassword);
        this.password = new Password(newPassword, passwordEncoder);
    }

    public void verifyPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        if (!matchesPassword(rawPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
    }

    boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(rawPassword, password.getValue());
    }

    private void validatePasswordNotContainsBirthDate(String rawPassword) {
        if (rawPassword.contains(birthDate.getValueWithoutHyphen())) {
            throw new CoreException(ErrorType.BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED);
        }
    }
}
