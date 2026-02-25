package com.loopers.application.user.command;

import com.loopers.domain.user.vo.UserId;

public record ChangePasswordCommand(
        UserId userId,
        String newRawPassword
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UserId userId;
        private String newRawPassword;

        public Builder userId(UserId userId) {
            this.userId = userId;
            return this;
        }

        public Builder newRawPassword(String newRawPassword) {
            this.newRawPassword = newRawPassword;
            return this;
        }

        public ChangePasswordCommand build() {
            return new ChangePasswordCommand(userId, newRawPassword);
        }
    }

    @Override
    public String toString() {
        return "ChangePasswordCommand[userId=%s, newRawPassword=***]".formatted(userId);
    }
}
