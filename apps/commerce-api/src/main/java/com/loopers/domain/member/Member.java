package com.loopers.domain.member;

import java.time.ZonedDateTime;

/**
 * 순수 도메인 모델
 * 
 * JPA 어노테이션이 없는 순수한 도메인 객체입니다.
 * 영속성 관련 로직은 infrastructure 레이어의 MemberEntity에서 처리합니다.
 * 
 * 장점:
 * - 도메인 레이어가 인프라(JPA)에 의존하지 않음
 * - 테스트 시 JPA 없이도 도메인 로직 검증 가능
 * - 도메인 모델 변경 시 DB 스키마와 독립적
 */
public class Member {

    private final Long id;
    private final String loginId;
    private String password;
    private final String name;
    private final String email;
    private final String birthDate;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;

    // 신규 생성 시 사용 (ID 없음)
    public Member(LoginId loginId, String encodedPassword, MemberName name, Email email, BirthDate birthDate) {
        this.id = null;
        this.loginId = loginId.value();
        this.password = encodedPassword;
        this.name = name.value();
        this.email = email.value();
        this.birthDate = birthDate.value();
        this.createdAt = null;
        this.updatedAt = null;
    }

    // 영속성에서 복원 시 사용 (ID 있음)
    private Member(Long id, String loginId, String password, String name, String email,
            String birthDate, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        this.id = id;
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 영속성에서 복원할 때 사용하는 정적 팩토리 메서드
     */
    public static Member withId(Long id, String loginId, String password, String name,
            String email, String birthDate,
            ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        return new Member(id, loginId, password, name, email, birthDate, createdAt, updatedAt);
    }

    public void changePassword(String encodedNewPassword) {
        this.password = encodedNewPassword;
    }

    // Getters
    public Long getId() {
        return id;
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

    public String getEmail() {
        return email;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
