package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    protected MemberModel() {}

    private MemberModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static MemberModel signUp(String loginId, String encodedPassword, String name, LocalDate birthDate, String email) {
        return new MemberModel(loginId, encodedPassword, name, birthDate, email);
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }
}
