package com.loopers.application.user.command;

import com.loopers.domain.user.vo.UserId;

public record AuthenticateCommand(
        UserId userId,
        String rawPassword
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UserId userId;
        private String rawPassword;

        public Builder userId(UserId userId) {
            this.userId = userId;
            return this;
        }

        public Builder rawPassword(String rawPassword) {
            this.rawPassword = rawPassword;
            return this;
        }

        public AuthenticateCommand build() {
            return new AuthenticateCommand(userId, rawPassword);
        }
    }

    @Override
    public String toString() {
        return "AuthenticateCommand[userId=%s, rawPassword=***]".formatted(userId);
    }
}
