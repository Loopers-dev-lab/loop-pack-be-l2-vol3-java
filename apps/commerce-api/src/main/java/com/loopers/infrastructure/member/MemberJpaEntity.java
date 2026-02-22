package com.loopers.infrastructure.member;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberJpaEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false, unique = true)
    private String memberId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    private MemberJpaEntity(String memberId, String password, String name, String email, LocalDate birthDate) {
        this.memberId = memberId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
    }

    public static MemberJpaEntity from(Member member) {
        return new MemberJpaEntity(
                member.getMemberId().getValue(),
                member.getPassword().getValue(),
                member.getName().getValue(),
                member.getEmail().getValue(),
                member.getBirthDate().getValue()
        );
    }

    public Member toDomain() {
        return Member.of(
                getId(),
                new MemberId(memberId),
                Password.ofEncoded(password),
                new Name(name),
                new Email(email),
                new BirthDate(birthDate)
        );
    }

    public void update(Member member) {
        this.password = member.getPassword().getValue();
    }
}
