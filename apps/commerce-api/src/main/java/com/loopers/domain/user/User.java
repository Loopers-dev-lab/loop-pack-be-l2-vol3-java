package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.UserName;
import java.time.ZonedDateTime;

/**
 * 사용자 엔티티 (Aggregate Root)
 * 순수 POJO - JPA 어노테이션 없음
 *
 * 각 필드는 Value Object로 자체 검증을 수행하며,
 * password는 암호화된 값만 저장한다 (평문 저장 금지).
 */
public class User {

    private Long id;
    private LoginId loginId;
    private String password;
    private UserName name;
    private BirthDate birthDate;
    private Email email;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected User() {}

    private User(LoginId loginId, String encodedPassword, UserName name, BirthDate birthDate, Email email) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static User create(LoginId loginId, String encodedPassword, UserName name, BirthDate birthDate, Email email) {
        return new User(loginId, encodedPassword, name, birthDate, email);
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static User reconstitute(Long id, LoginId loginId, String password, UserName name,
                                     BirthDate birthDate, Email email,
                                     ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        User user = new User();
        user.id = id;
        user.loginId = loginId;
        user.password = password;
        user.name = name;
        user.birthDate = birthDate;
        user.email = email;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        user.deletedAt = deletedAt;
        return user;
    }

    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    public LoginId getLoginId() {
        return this.loginId;
    }

    public String getPassword() {
        return this.password;
    }

    public UserName getName() {
        return this.name;
    }

    public BirthDate getBirthDate() {
        return this.birthDate;
    }

    public Email getEmail() {
        return this.email;
    }

    public Long getId() {
        return this.id;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}
