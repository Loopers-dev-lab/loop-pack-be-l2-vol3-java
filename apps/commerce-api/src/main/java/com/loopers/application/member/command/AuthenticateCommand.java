package com.loopers.application.member.command;

import com.loopers.domain.member.vo.MemberId;

public record AuthenticateCommand(
        MemberId memberId,
        String rawPassword
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private MemberId memberId;
        private String rawPassword;

        public Builder memberId(MemberId memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder rawPassword(String rawPassword) {
            this.rawPassword = rawPassword;
            return this;
        }

        public AuthenticateCommand build() {
            return new AuthenticateCommand(memberId, rawPassword);
        }
    }

    @Override
    public String toString() {
        return "AuthenticateCommand[memberId=%s, rawPassword=***]".formatted(memberId);
    }
}
