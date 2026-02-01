package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;

    protected MemberModel() {}

    private MemberModel(String loginId, String password, String name,
                        LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static MemberModel create(String loginId, String rawPassword,
                                      String name, LocalDate birthDate,
                                      String email, PasswordEncoder encoder) {
        String encodedPassword = encoder.encode(rawPassword);
        return new MemberModel(loginId, encodedPassword, name, birthDate, email);
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
