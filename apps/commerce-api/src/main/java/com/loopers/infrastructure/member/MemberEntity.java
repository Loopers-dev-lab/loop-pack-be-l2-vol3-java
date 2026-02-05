package com.loopers.infrastructure.member;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA 영속성 엔티티
 * 
 * 도메인 모델(Member)과 분리된 영속성 전용 클래스입니다.
 * - @Entity, @Table, @Column 등 JPA 어노테이션은 여기에만 존재
 * - Domain ↔ Entity 변환 메서드 제공
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class MemberEntity extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    private String loginId;

    @Column(name = "password", nullable = false, length = 60)
    private String password;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "birth_date", nullable = false, length = 8)
    private String birthDate;

    private MemberEntity(String loginId, String password, String name, String email, String birthDate) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
    }

    /**
     * Domain → Entity 변환 (정적 팩토리 메서드)
     */
    public static MemberEntity from(Member member) {
        return new MemberEntity(
                member.getLoginId(),
                member.getPassword(),
                member.getName(),
                member.getEmail(),
                member.getBirthDate());
    }

    /**
     * 테스트용 직접 생성 팩토리 메서드
     * Value Object 없이 문자열로 직접 생성
     */
    public static MemberEntity create(String loginId, String password, String name, String email, String birthDate) {
        return new MemberEntity(loginId, password, name, email, birthDate);
    }

    /**
     * Entity → Domain 변환
     */
    public Member toMember() {
        return Member.withId(
                this.getId(),
                this.loginId,
                this.password,
                this.name,
                this.email,
                this.birthDate,
                this.getCreatedAt(),
                this.getUpdatedAt());
    }

    /**
     * 비밀번호 변경 (영속성 엔티티에서 직접 변경)
     */
    public void changePassword(String encodedNewPassword) {
        this.password = encodedNewPassword;
    }
}
