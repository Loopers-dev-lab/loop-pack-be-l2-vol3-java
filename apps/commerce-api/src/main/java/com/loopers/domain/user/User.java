package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.UserName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 사용자 엔티티 (Aggregate Root)
 *
 * 각 필드는 Value Object로 자체 검증을 수행하며,
 * password는 암호화된 값만 저장한다 (평문 저장 금지).
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Embedded
    private LoginId loginId;

    /** 암호화된 비밀번호 (BCrypt 해시) */
    @Column(name = "password", nullable = false)
    private String password;

    @Embedded
    private UserName name;

    @Embedded
    private BirthDate birthDate;

    @Embedded
    private Email email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    protected User() {}

    private User(LoginId loginId, String encodedPassword, UserName name, BirthDate birthDate, Email email, Gender gender) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.gender = gender;
    }

    public static User create(LoginId loginId, String encodedPassword, UserName name, BirthDate birthDate, Email email, Gender gender) {
        if (gender == null) {
            throw new CoreException(UserErrorType.INVALID_GENDER, "성별은 필수입니다.");
        }
        return new User(loginId, encodedPassword, name, birthDate, email, gender);
    }

    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    public LoginId getLoginId() {
        return this.loginId;
    }

    public String getPassword() {
        return this.password;
    }

    public UserName getName() {
        return this.name;
    }

    public BirthDate getBirthDate() {
        return this.birthDate;
    }

    public Email getEmail() {
        return this.email;
    }

    public Gender getGender() {
        return this.gender;
    }
}
