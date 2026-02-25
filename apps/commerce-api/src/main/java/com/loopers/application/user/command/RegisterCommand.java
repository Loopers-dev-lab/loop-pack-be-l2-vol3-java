package com.loopers.application.user.command;

public record RegisterCommand(
        String userId,
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
        private String userId;
        private String rawPassword;
        private String name;
        private String email;
        private String birthDate;
        private String phone;

        public Builder userId(String userId) {
            this.userId = userId;
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
            return new RegisterCommand(userId, rawPassword, name, email, birthDate, phone);
        }
    }

    @Override
    public String toString() {
        return "RegisterCommand[userId=%s, rawPassword=***, name=%s, email=%s, birthDate=%s, phone=%s]"
                .formatted(userId, name, email, birthDate, phone);
    }
}
