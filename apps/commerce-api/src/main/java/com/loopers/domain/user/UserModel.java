package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Embedded
    private Password password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Embedded
    private Email email;

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]*$");

    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣]+$");

    public UserModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        validateCommonFields(loginId, name, birthDate);
        this.loginId = loginId;
        this.password = new Password(password, birthDate);
        this.name = name;
        this.birthDate = birthDate;
        this.email = new Email(email);
    }

    public static UserModel createWithEncodedPassword(String loginId, String encodedPassword, String name, LocalDate birthDate, String email) {
        UserModel user = new UserModel();
        user.validateCommonFields(loginId, name, birthDate);
        user.loginId = loginId;
        user.password = Password.ofEncoded(encodedPassword);
        user.name = name;
        user.birthDate = birthDate;
        user.email = new Email(email);
        return user;
    }

    private void validateCommonFields(String loginId, String name, LocalDate birthDate) {
        if (!StringUtils.hasText(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
        if (!StringUtils.hasText(name)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글만 가능합니다.");
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래 날짜일 수 없습니다.");
        }
        if (birthDate.isBefore(LocalDate.now().minusYears(150))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 150년 이전일 수 없습니다.");
        }
    }

    public void changePassword(String newEncodedPassword) {
        this.password = Password.ofEncoded(newEncodedPassword);
    }
}

