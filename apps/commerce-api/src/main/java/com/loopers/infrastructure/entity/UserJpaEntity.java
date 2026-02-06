package com.loopers.infrastructure.entity;

import com.loopers.domain.model.Birthday;
import com.loopers.domain.model.Email;
import com.loopers.domain.model.UserId;
import com.loopers.domain.model.UserName;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String userId;

    @Column(nullable = false)
    private String encodedPassword;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected UserJpaEntity() {}


    public UserJpaEntity(Long id, UserId userId, String encodedPassword, UserName userName, Birthday birth, Email email, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId.getValue();
        this.encodedPassword = encodedPassword;
        this.username = userName.getValue();
        this.birthday = birth.getValue();
        this.email = email.getValue();
        this.createdAt = createdAt;
    }
}
