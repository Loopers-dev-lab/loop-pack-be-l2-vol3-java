package com.loopers.domain.user;

public record NewUser(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email
) {
}
