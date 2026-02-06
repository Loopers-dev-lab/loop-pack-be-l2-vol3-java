package com.loopers.domain.model;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {
    private Long id;
    private final UserId userId;
    private final UserName userName;
    private final String encodedPassword;
    private final Birthday birth; // YYYYMMDD format with default value
    private final Email email;
    private final WrongPasswordCount wrongPasswordCount;
    private LocalDateTime createdAt;

    public static User register(UserId userId,UserName userName, String encodedPassword, Birthday birth, Email email, WrongPasswordCount wrongPasswordCount, LocalDateTime createdAt) {
        return new User(null,userId,userName,encodedPassword,birth,email, wrongPasswordCount,createdAt);
    }

    public static User reconstitute(Long id, UserId userId, UserName userName, String encodedPassword, Birthday birth, Email email, WrongPasswordCount wrongPasswordCount, LocalDateTime createdAt) {
        return new User(id, userId, userName, encodedPassword, birth, email, wrongPasswordCount, createdAt);
    }

    public boolean matchesPassword(Password password, PasswordMatchChecker checker) {
        return checker.matches(password, this.encodedPassword);
    }

    public User changePassword(String newEncodedPassword) {
        return new User(
                this.id,
                this.userId,
                this.userName,
                newEncodedPassword,
                this.birth,
                this.email,
                this.wrongPasswordCount,
                this.createdAt
        );
    }

    @FunctionalInterface
    public interface PasswordMatchChecker {
        boolean matches(Password password, String encodingPassword);
    }
}
