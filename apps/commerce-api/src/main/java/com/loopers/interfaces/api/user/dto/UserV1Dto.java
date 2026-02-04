package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {
    public record UserResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                    info.loginId(),
                    info.name(),
                    info.birthDate(),
                    info.email()
            );
        }
    }
}
