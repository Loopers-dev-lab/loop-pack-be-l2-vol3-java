package com.loopers.domain.user;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * 사용자(User) JPA 엔티티.
 * 로그인 ID, 비밀번호, 이름, 생년월일, 이메일, 주소를 관리한다.
 * {@link BaseStringIdEntity}를 상속하여 UUID PK와 소프트 삭제를 지원한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseStringIdEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]+$");
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(length = 8)
    private String birthday;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String address;

    private UserModel(String loginId, String password, String userName,
                      String birthday, String email, String address) {
        this.loginId = loginId;
        this.password = password;
        this.userName = userName;
        this.birthday = birthday;
        this.email = email;
        this.address = address;
    }

    /**
     * 이미 인코딩된 비밀번호로 UserModel 인스턴스를 생성한다.
     * loginId는 영숫자만 허용, userName은 필수값.
     *
     * @param loginId         로그인 ID (영숫자만 허용)
     * @param encodedPassword 인코딩된 비밀번호 (필수)
     * @param userName        사용자 이름 (필수)
     * @param birthday        생년월일 (YYYYMMDD)
     * @param email           이메일
     * @param address         주소
     * @return 생성된 UserModel 인스턴스
     * @throws CoreException loginId가 null/blank/특수문자 포함 시 (BAD_REQUEST)
     */
    public static UserModel createWithEncodedPassword(
            String loginId, String encodedPassword, String userName,
            String birthday, String email, String address) {
        validateLoginId(loginId);
        validateEncodedPassword(encodedPassword);
        validateUserName(userName);
        return new UserModel(loginId, encodedPassword, userName, birthday, email, address);
    }

    /**
     * 평문 비밀번호가 보안 정책을 충족하는지 검증한다.
     * 8~16자, 영문대소문자+숫자+특수문자만 허용, 생년월일 포함 불가.
     *
     * @param rawPassword 평문 비밀번호
     * @param birthday    생년월일 (포함 여부 검사용)
     * @throws CoreException 정책 위반 시 (INVALID_PASSWORD)
     */
    public static void validatePassword(String rawPassword, String birthday) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD, "비밀번호는 필수입니다.");
        }
        if (rawPassword.length() < PASSWORD_MIN_LENGTH || rawPassword.length() > PASSWORD_MAX_LENGTH) {
            throw new CoreException(ErrorType.INVALID_PASSWORD,
                    String.format("비밀번호는 %d~%d자여야 합니다.", PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH));
        }
        if (!PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD, "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.");
        }
        if (birthday != null && !birthday.isBlank() && rawPassword.contains(birthday)) {
            throw new CoreException(ErrorType.INVALID_PASSWORD, "생년월일은 비밀번호에 포함될 수 없습니다.");
        }
    }

    /**
     * 개인정보 보호를 위해 이름의 마지막 글자를 '*'로 대체한 값을 반환한다.
     * 1글자 이름은 전체 마스킹("*"), 2글자 이상은 마지막 글자만 마스킹.
     *
     * @return 마스킹된 이름 (예: "김대진" → "김대*")
     */
    public String getMaskedName() {
        if (userName == null || userName.isEmpty()) {
            return userName;
        }
        if (userName.length() == 1) {
            return "*";
        }
        return userName.substring(0, userName.length() - 1) + "*";
    }

    /**
     * 인코딩된 새 비밀번호로 교체한다.
     *
     * @param encodedPassword 새 인코딩된 비밀번호 (필수)
     * @throws CoreException encodedPassword가 blank인 경우 (BAD_REQUEST)
     */
    public void updatePassword(String encodedPassword) {
        validateEncodedPassword(encodedPassword);
        this.password = encodedPassword;
    }

    /**
     * JPA @PrePersist/@PreUpdate 시 호출되는 유효성 검증 훅.
     */
    @Override
    protected void guard() {
        validateLoginId(this.loginId);
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private static void validateEncodedPassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "암호화된 비밀번호는 필수입니다.");
        }
    }

    private static void validateUserName(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
    }
}
