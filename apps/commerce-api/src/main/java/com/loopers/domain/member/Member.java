package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private Password password;

    @Column(nullable = false, length = 50)
    private String name;

    @Embedded
    private BirthDate birthDate;

    @Embedded
    private Email email;

    protected Member() {}

    public Member(LoginId loginId, Password password, String name,
                  BirthDate birthDate, Email email) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public LoginId getLoginId() { return loginId; }
    public Password getPassword() { return password; }
    public String getName() { return name; }
    public BirthDate getBirthDate() { return birthDate; }
    public Email getEmail() { return email; }

    public void changePassword(Password newPassword) {
        this.password = newPassword;
    }
}
