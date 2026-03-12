package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseEntity {

    @Column(name = "login_id")
    private String loginId;

    @Column(name = "password")
    private String password;

    @Column(name = "name")
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "email")
    private String email;

    @Column(name = "point")
    private long point;

    @Version
    private Long version;

    // === 생성 === //

    private UserModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.point = 0L;
    }

    public static UserModel create(String loginId, String encryptedPassword,
                                   String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateBirthDate(birthDate);
        validateEmail(email);
        return new UserModel(loginId, encryptedPassword, name, birthDate, email);
    }

    // === 도메인 로직 === //

    public void changePassword(String newEncryptedPassword) {
        this.password = newEncryptedPassword;
    }

    public void addPoint(long amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트 증가량은 0보다 커야 합니다.");
        }
        this.point += amount;
    }

    public void deductPoint(long amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트 차감량은 0보다 커야 합니다.");
        }
        if (this.point < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다.");
        }
        this.point -= amount;
    }

    public String getBirthDateString() {
        return birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    // === 검증 === //

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]*$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (loginId.length() < 4 || loginId.length() > 12) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 4자에서 12자 사이여야 합니다.");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (name.length() < 2 || name.length() > 10) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 이름 길이입니다.");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수 입력값입니다.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 과거 날짜여야 합니다.");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }
}
