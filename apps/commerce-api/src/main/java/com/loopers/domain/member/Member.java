package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;

    protected Member() {}

    private Member(String loginId, String password, String name,
                   LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static Member create(String loginId, String rawPassword,
                                String name, LocalDate birthDate,
                                String email, PasswordEncoder encoder) {
        validateLoginId(loginId);
        validatePassword(rawPassword, birthDate);
        String normalizedName = normalizeName(name);
        validateName(normalizedName);
        validateEmail(email);

        String encodedPassword = encoder.encode(rawPassword);
        return new Member(loginId, encodedPassword, normalizedName, birthDate, email);
    }

    private static void validatePassword(String password, LocalDate birthDate) {
        if (password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
        if (!password.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.");
        }
        String birthDateStr = birthDate.toString().replace("-", ""); // 19900115
        if (password.contains(birthDateStr)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static void validateLoginId(String loginId) {
        if (!loginId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static void validateName(String name) {
        boolean isKorean = name.matches("^[가-힣]+$");
        boolean isEnglish = name.matches("^[a-zA-Z]+( [a-zA-Z]+)*$");

        if (!isKorean && !isEnglish) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글만 또는 영문만 허용됩니다.");
        }
    }

    private static void validateEmail(String email) {
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }

    // Getter
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
