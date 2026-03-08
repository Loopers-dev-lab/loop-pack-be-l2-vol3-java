package com.loopers.application.user;

import com.loopers.domain.user.NewUser;

public class UserCommand {

    public record SignUpCommand(String loginId, String password, String name, String birthDate, String email) {

        public NewUser toNewUser() {
            return new NewUser(loginId, password, name, birthDate, email);
        }
    }
}
