package com.loopers.domain.model.user;

import java.time.LocalDateTime;

public record UserData(
        Long id,
        UserId userId,
        UserName userName,
        String encodedPassword,
        Birthday birth,
        Email email,
        int wrongPasswordCount,
        LocalDateTime createdAt
) {
}
