package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

public class UserV1Dto {

    public record SignUpRequest(String loginId, String password, String name, String birthday, String email) {
    }

    public record UserResponse(String loginId, String name, String birthday, String email) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(info.loginId(), info.name(), info.birthday(), info.email());
        }
    }
}
