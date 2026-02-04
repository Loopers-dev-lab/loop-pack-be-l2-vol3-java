package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@Getter
public class User extends BaseEntity {

    private static final Pattern PASSWORD_CHAR_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");

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

    protected User() {}

    public User(String loginId, String encryptedPassword, String name, String birthday, String email) {
        this.loginId = loginId;
        this.password = encryptedPassword;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    public String getMaskedName() {
        if (name.length() <= 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
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
}
