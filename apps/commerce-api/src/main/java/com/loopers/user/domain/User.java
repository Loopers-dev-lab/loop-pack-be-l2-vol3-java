package com.loopers.user.domain;


import lombok.Getter;

@Getter
public class User {
    private String loginId;
    private String password;
    private String name;
    private String birthDate;
    private String email;

    public User(String loginId, String password, String name, String birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

}
