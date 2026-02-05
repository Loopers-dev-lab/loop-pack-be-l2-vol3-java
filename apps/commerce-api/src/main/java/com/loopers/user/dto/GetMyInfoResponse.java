package com.loopers.user.dto;

import com.loopers.user.domain.User;

public record GetMyInfoResponse(
        String loginId,
        String name,
        String birthDate,
        String email
) {
    public static GetMyInfoResponse from(User user) {
        return new GetMyInfoResponse(
                user.getLoginId(),
                user.getMaskedName(),
                user.getBirthDate(),
                user.getEmail()
        );
    }
}
