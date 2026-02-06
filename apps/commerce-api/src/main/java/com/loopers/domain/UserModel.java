package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "login_id"))
    private LoginId loginId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "password"))
    private Password password;

    @Embedded
    @AttributeOverride(name = "name", column = @Column(name = "name"))
    private Name name;

    @Embedded
    @AttributeOverride(name = "birthDate", column = @Column(name = "birth_date"))
    private BirthDate birthDate;

    @Embedded
    @AttributeOverride(name = "mail", column = @Column(name = "email"))
    private Email email;

    public UserModel(LoginId loginId, Password password, Name name, BirthDate birthDate, Email email) {
        validate(loginId, password, name, birthDate, email);
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    private void validate(LoginId loginId, Password password, Name name, BirthDate birthDate, Email email) {
        validateNotNull(loginId, "로그인 ID");
        validateNotNull(password, "비밀번호");
        validateNotNull(name, "이름");
        validateNotNull(birthDate, "생년월일");
        validateNotNull(email, "이메일");
    }
    private void validateNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST,fieldName + "은(는) 필수 입력값입니다.");
        }
    }

    public void changePassword(Password currentPassword, Password newPassword) {
        // 검증은 UserService에서 수행
        this.password = newPassword;
    }
}
