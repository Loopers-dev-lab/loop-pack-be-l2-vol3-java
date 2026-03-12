package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*]+$");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd")
        .withResolverStyle(ResolverStyle.STRICT);

    private static final int MAX_LOGIN_ID_BYTES = 30;
    private static final int MAX_NAME_BYTES = 30;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 16;
    private static final int BIRTH_DATE_SUBSTRING_LENGTH = 4;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private String birthDate;

    @Column(name = "email", nullable = false)
    private String email;

    protected User() {}

    public User(String loginId, String password, String name, String birthDate, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateBirthDate(birthDate);
        validateEmail(email);
        validatePassword(password, birthDate);

        this.loginId = loginId;
        this.password = PASSWORD_ENCODER.encode(password);
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getName() {
        return name;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public String getEmail() {
        return email;
    }

    public boolean matchPassword(String rawPassword) {
        return PASSWORD_ENCODER.matches(rawPassword, this.password);
    }

    public void changePassword(String newPassword) {
        if (matchPassword(newPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 다르게 설정해야 합니다.");
        }
        validatePassword(newPassword, this.birthDate);
        this.password = PASSWORD_ENCODER.encode(newPassword);
    }

    public String getMaskedName() {
        if (name.length() <= 1) {
            return name;
        }
        char first = name.charAt(0);
        char last = name.charAt(name.length() - 1);
        String middle = "*".repeat(name.length() - 2);
        return first + middle + last;
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용할 수 있습니다.");
        }
        if (loginId.getBytes(StandardCharsets.UTF_8).length > MAX_LOGIN_ID_BYTES) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 30바이트를 초과할 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글과 영문만 사용할 수 있습니다.");
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 30바이트를 초과할 수 없습니다.");
        }
    }

    private void validateBirthDate(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }
        if (birthDate.length() != 8 || !birthDate.matches("\\d{8}")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 YYYYMMDD 형식이어야 합니다.");
        }
        try {
            LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 날짜입니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 이메일 형식입니다.");
        }
    }

    private void validatePassword(String password, String birthDate) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다.");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 16자 이하이어야 합니다.");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자(!@#$%^&*)만 사용할 수 있습니다.");
        }
        if (containsBirthDateSubstring(password, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일 정보를 포함할 수 없습니다.");
        }
    }

    private boolean containsBirthDateSubstring(String password, String birthDate) {
        for (int i = 0; i <= birthDate.length() - BIRTH_DATE_SUBSTRING_LENGTH; i++) {
            String substring = birthDate.substring(i, i + BIRTH_DATE_SUBSTRING_LENGTH);
            if (password.contains(substring)) {
                return true;
            }
        }
        return false;
    }
}
