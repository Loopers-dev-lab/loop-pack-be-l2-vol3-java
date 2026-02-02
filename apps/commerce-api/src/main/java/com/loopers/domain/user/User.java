package com.loopers.domain.user;

import lombok.Getter;

@Getter
public class User {
    private String email;
    private String password;

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
