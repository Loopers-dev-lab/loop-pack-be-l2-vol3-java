package com.loopers.infrastructure.member;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "member")
public class MemberEntity extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "birthday", nullable = false)
    private LocalDate birthday;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    protected MemberEntity() {}

    public static MemberEntity from(Member member) {
        MemberEntity entity = new MemberEntity();
        entity.loginId = member.getLoginId();
        entity.password = member.getPassword();
        entity.name = member.getName();
        entity.birthday = member.getBirthday();
        entity.email = member.getEmail();
        return entity;
    }

    public Member toDomain() {
        return new Member(getId(), loginId, password, name, birthday, email);
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public String getEmail() {
        return email;
    }
}