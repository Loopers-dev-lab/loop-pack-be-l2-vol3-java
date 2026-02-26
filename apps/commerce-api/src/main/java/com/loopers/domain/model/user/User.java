package com.loopers.domain.model.user;


import com.loopers.domain.service.PasswordEncoder;
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
    private final int wrongPasswordCount;
    private final LocalDateTime createdAt;

    public static User register(UserId userId, UserName userName, String encodedPassword,
                                Birthday birth, Email email, LocalDateTime createdAt) {
        return new User(null, userId, userName, encodedPassword, birth, email, 0, createdAt);
    }

    public static User reconstitute(UserData data) {
        return new User(data.id(), data.userId(), data.userName(), data.encodedPassword(),
                data.birth(), data.email(), data.wrongPasswordCount(), data.createdAt());
    }

    public boolean matchesPassword(Password password, PasswordMatchChecker checker) {
        return checker.matches(password, this.encodedPassword);
    }

    public User changePassword(String currentRawPassword, String newRawPassword,
                               PasswordEncoder encoder) {
        if (!encoder.matches(currentRawPassword, this.encodedPassword)) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        Password newPassword = Password.of(newRawPassword, this.birth.getValue());
        if (encoder.matches(newPassword.getValue(), this.encodedPassword)) {
            throw new IllegalArgumentException("현재 비밀번호는 사용할 수 없습니다.");
        }
        String encodedNewPassword = encoder.encrypt(newPassword.getValue());
        return new User(
                this.id,
                this.userId,
                this.userName,
                encodedNewPassword,
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
