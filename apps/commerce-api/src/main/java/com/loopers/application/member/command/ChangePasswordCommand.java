package com.loopers.application.member.command;

import com.loopers.domain.member.vo.MemberId;

public record ChangePasswordCommand(
        MemberId memberId,
        String newRawPassword
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private MemberId memberId;
        private String newRawPassword;

        public Builder memberId(MemberId memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder newRawPassword(String newRawPassword) {
            this.newRawPassword = newRawPassword;
            return this;
        }

        public ChangePasswordCommand build() {
            return new ChangePasswordCommand(memberId, newRawPassword);
        }
    }

    @Override
    public String toString() {
        return "ChangePasswordCommand[memberId=%s, newRawPassword=***]".formatted(memberId);
    }
}
