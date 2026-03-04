package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_users_login_id", columnNames = {"login_id"})
})
public class User extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private Password password;

    @Embedded
    private UserName name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Embedded
    private Email email;

    @Version
    @Column(name = "version")
    private Long version;

    protected User() {}

    public User(String loginId, String encryptedPassword, String name, LocalDate birthDate, String email) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }

        this.loginId = new LoginId(loginId);
        this.password = new Password(encryptedPassword);
        this.name = new UserName(name);
        this.birthDate = birthDate;
        this.email = new Email(email);
    }

    public String getLoginId() {
        return loginId.getValue();
    }

    public String getPassword() {
        return password.getEncryptedValue();
    }

    public String getName() {
        return name.getValue();
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getEmail() {
        return email.getValue();
    }

    public void changePassword(String newEncryptedPassword) {
        this.password = new Password(newEncryptedPassword);
    }

    public String getMaskedName() {
        return name.mask();
    }
}
