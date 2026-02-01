package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

@Entity
@Table(name = "member")
@Getter
public class Member extends BaseEntity {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z가-힣]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern BIRTHDAY_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Pattern PASSWORD_CHAR_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");
    private static final DateTimeFormatter BIRTHDAY_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd")
        .withResolverStyle(ResolverStyle.STRICT);

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "birthday", nullable = false, length = 8)
    private String birthday;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    protected Member() {}

    public Member(String loginId, String encryptedPassword, String name, String birthday, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateBirthday(birthday);
        validateEmail(email);

        this.loginId = loginId;
        this.password = encryptedPassword;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    public static void validateRawPassword(String rawPassword, String birthday) {
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상 16자 이하여야 합니다.");
        }
        if (!PASSWORD_CHAR_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다.");
        }
        if (birthday != null && rawPassword.contains(birthday)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생일을 포함할 수 없습니다.");
        }
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문만 사용할 수 있습니다.");
        }
    }

    private void validateBirthday(String birthday) {
        if (birthday == null || birthday.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생일은 비어있을 수 없습니다.");
        }
        if (!BIRTHDAY_PATTERN.matcher(birthday).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생일은 yyyyMMdd 형식이어야 합니다.");
        }
        try {
            LocalDate.parse(birthday, BIRTHDAY_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생일이 유효한 날짜가 아닙니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }
}
