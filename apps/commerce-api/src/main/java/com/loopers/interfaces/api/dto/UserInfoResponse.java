package com.loopers.interfaces.api.dto;

import com.loopers.application.UserQueryUseCase;

import java.time.format.DateTimeFormatter;

public record UserInfoResponse(
        String loginId,
        String name,
        String birthday,
        String email
) {
    private static final DateTimeFormatter BIRTHDAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static UserInfoResponse from(UserQueryUseCase.UserInfoResponse userInfo) {
        return new UserInfoResponse(
                userInfo.loginId(),
                userInfo.maskedName(),
                userInfo.birthday().format(BIRTHDAY_FORMATTER),
                userInfo.email()
        );
    }
}
