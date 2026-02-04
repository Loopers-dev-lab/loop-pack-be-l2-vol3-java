package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.security.PasswordEncryptor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@Getter
public class User extends BaseEntity {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{1,10}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*(),.?\":{}|<>]{8,16}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z]{2,}$");

    @Column(name = "login_id", unique = true, nullable = false, length = 10)
    private String loginId;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    protected User() {}

    private User(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static User register(String loginId, String password, String name, LocalDate birthDate, String email, PasswordEncryptor encryptor) {
        validateLoginId(loginId);
        validateName(name);
        validateBirthDate(birthDate);
        validatePassword(password, birthDate);
        validateEmail(email);

        String encryptedPassword = encryptor.encode(password);
        return new User(loginId, encryptedPassword, name, birthDate, email);
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("생년월일 형식이 올바르지 않습니다");
        }

        LocalDate now = LocalDate.now();
        if (birthDate.isAfter(now)) {
            throw new IllegalArgumentException("생년월일 형식이 올바르지 않습니다");
        }

        // 너무 오래된 날짜도 검증 (예: 1900년 이전)
        LocalDate minDate = LocalDate.of(1900, 1, 1);
        if (birthDate.isBefore(minDate)) {
            throw new IllegalArgumentException("생년월일 형식이 올바르지 않습니다");
        }
    }

    private static void validateLoginId(String loginId) {
        if (!ID_PATTERN.matcher(loginId).matches()) {
            throw new IllegalArgumentException("ID 형식이 올바르지 않습니다");
        }
    }

    private static void validateName(String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("이름 형식이 올바르지 않습니다");
        }
    }

    private static void validatePassword(String password, LocalDate birthDate) {
        String birthDateStr = birthDate.toString().replace("-", "");
        if (password.contains(birthDateStr)) {
            throw new IllegalArgumentException("생년월일은 비밀번호 내에 포함될 수 없습니다");
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("비밀번호 형식이 올바르지 않습니다");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }

        String[] parts = email.split("@");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }

        String domain = parts[1];
        if (domain.startsWith(".") || !domain.contains(".")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }
    }
}


