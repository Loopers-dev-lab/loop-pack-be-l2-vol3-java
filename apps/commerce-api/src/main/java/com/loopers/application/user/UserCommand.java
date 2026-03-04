package com.loopers.application.user;

import java.time.LocalDate;

public record UserCommand() {

    public record SignUp(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        public static SignUp of(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
            return new SignUp(loginId, rawPassword, name, birthDate, email);
        }
    }

    public record ChangePassword(Long id, String newRawPassword) {
        public static ChangePassword of(Long id, String newRawPassword) {
            return new ChangePassword(id, newRawPassword);
        }
    }
}
