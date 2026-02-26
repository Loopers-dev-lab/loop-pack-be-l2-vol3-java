package com.loopers.infrastructure.member;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.member.vo.Phone;
import com.loopers.domain.member.vo.MemberId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name = "members")
public class MemberEntity extends BaseEntity {

    @Getter
    @Column(name = "member_id", nullable = false, unique = true)
    private String memberId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "phone")
    private String phone;

    protected MemberEntity() {
    }

    public MemberEntity(String memberId, String password, String name, String email, LocalDate birthDate, String phone) {
        this.memberId = memberId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
        this.phone = phone;
    }

    public static MemberEntity from(Member member) {
        return new MemberEntity(
                member.id().value(),
                member.password().value(),
                member.name().value(),
                member.email().value(),
                member.birthDate().value(),
                member.phone() != null ? member.phone().value() : null
        );
    }

    public Member toDomain() {
        return new Member(
                new MemberId(memberId),
                Password.ofEncoded(password),
                new Name(name),
                new Email(email),
                new BirthDate(birthDate),
                phone != null ? new Phone(phone) : null
        );
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateFrom(Member member) {
        this.password = member.password().value();
        this.name = member.name().value();
        this.email = member.email().value();
        this.birthDate = member.birthDate().value();
        this.phone = member.phone() != null ? member.phone().value() : null;
    }
}
