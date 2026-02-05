package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private static final String PASSWORD_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$";
    private static final String LOGIN_ID_PATTERN = "^[a-zA-Z0-9]+$";

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    protected User() {}

    public User(String loginId, String encodedPassword, String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateBirthDate(birthDate);
        validateEmail(email);

        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static void validateRawPassword(String rawPassword, LocalDate birthDate) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (rawPassword.length() < 8 || rawPassword.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
        if (!rawPassword.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        }
        if (birthDate != null && containsBirthDate(rawPassword, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static boolean containsBirthDate(String password, LocalDate birthDate) {
        String yyyyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String yyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String yyyy_MM_dd = birthDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String yy_MM_dd = birthDate.format(DateTimeFormatter.ofPattern("yy-MM-dd"));

        return password.contains(yyyyMMdd) ||
               password.contains(yyMMdd) ||
               password.contains(yyyy_MM_dd) ||
               password.contains(yy_MM_dd);
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (!loginId.matches(LOGIN_ID_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용 가능합니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!email.matches("^[^@]+@[^@]+\\.[^@]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    public String getMaskedName() {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getEmail() {
        return email;
    }
}
