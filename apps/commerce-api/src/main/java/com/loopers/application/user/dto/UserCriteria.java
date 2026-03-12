package com.loopers.application.user.dto;

import com.loopers.domain.user.dto.UserCommand;

public class UserCriteria {

    public record Signup(String loginId, String rawPassword, String name, String birthDate, String email) {
        public UserCommand.Signup toCommand() {
            return new UserCommand.Signup(loginId, rawPassword, name, birthDate, email);
        }
    }

    public record ChangePassword(String rawCurrentPassword, String rawNewPassword) {
        public UserCommand.ChangePassword toCommand() {
            return new UserCommand.ChangePassword(rawCurrentPassword, rawNewPassword);
        }
    }
}
