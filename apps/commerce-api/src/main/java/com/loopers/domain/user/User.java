package com.loopers.domain.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "user")
public class User extends BaseEntity {

    private Long id;
    private String loginId;
    private String password; // encoded
    private String name;
    private LocalDate birthDate;
    private String email;

    protected User() {}

    private User(String loginId, String encodedPassword, String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validateEncodedPassword(encodedPassword);
        validateName(name);
        validateBirthDate(birthDate);
        validateEmail(email);

        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static User create(String loginId, String encodedPassword, String name, LocalDate birthDate, String email) {
        return new User(loginId, encodedPassword, name, birthDate, email);
    }

    public static User create(SignUpCommand command, String encodedPassword) {
        return new User(command.loginId(), encodedPassword, command.name(), command.birthDate(), command.email());

    }

    private void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수값입니다.");
        }

        if (!loginId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자만 가능합니다.");
        }
    }

    private void validateEncodedPassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수값입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수값입니다.");
        }

        if (name.length() < 2) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 최소 두글자 이상이어야 합니다.");
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수값입니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수값입니다.");
        }

        if (!email.contains("@") || !email.matches("^[\\w\\.]+@[\\w\\.]+\\.[a-zA-Z]{2,}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

    public void updatePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }
}
