package com.loopers.user.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import org.springframework.util.Assert;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, length = 10, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String birthDate;

    @Column(nullable = false)
    private String email;

    @Builder
    public User(String loginId, String password, String name, String birthDate, String email) {
        validateRequired(loginId, password, name, birthDate, email);

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    private void validateRequired(String loginId, String password, String name, String birthDate, String email) {
        Assert.hasText(loginId, "loginId는 필수입니다");
        Assert.hasText(password, "password는 필수입니다");
        Assert.hasText(name, "name은 필수입니다");
        Assert.hasText(birthDate, "birthDate는 필수입니다");
        Assert.hasText(email, "email은 필수입니다");
    }

    public String getMaskedName() {
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }

    public void changePassword(String newPassword) {
        Assert.hasText(newPassword, "새 비밀번호는 필수입니다");
        this.password = newPassword;
    }
}
