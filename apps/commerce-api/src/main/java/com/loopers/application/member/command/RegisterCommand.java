package com.loopers.application.member.command;

public record RegisterCommand(
        String memberId,
        String rawPassword,
        String name,
        String email,
        String birthDate,
        String phone
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String memberId;
        private String rawPassword;
        private String name;
        private String email;
        private String birthDate;
        private String phone;

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder rawPassword(String rawPassword) {
            this.rawPassword = rawPassword;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder birthDate(String birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public RegisterCommand build() {
            return new RegisterCommand(memberId, rawPassword, name, email, birthDate, phone);
        }
    }

    @Override
    public String toString() {
        return "RegisterCommand[memberId=%s, rawPassword=***, name=%s, email=%s, birthDate=%s, phone=%s]"
                .formatted(memberId, name, email, birthDate, phone);
    }
}
