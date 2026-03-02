package com.loopers.infrastructure.member.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.member.model.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "member")
@SQLRestriction("deleted_at IS NULL")
public class MemberEntity extends BaseEntity {

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

    protected MemberEntity() {}

    private MemberEntity(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static MemberEntity toEntity(Member model) {
        return new MemberEntity(
            model.getLoginId().value(),
            model.getPassword().value(),
            model.getName().value(),
            model.getBirthDate().value(),
            model.getEmail().value()
        );
    }

    public Member toModel() {
        return Member.reconstruct(
            this.getId(),
            this.loginId,
            this.password,
            this.name,
            this.birthDate,
            this.email
        );
    }

    public void changePassword(String password) {
        this.password = password;
    }
}
