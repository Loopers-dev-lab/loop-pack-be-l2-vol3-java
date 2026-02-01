package com.loopers.user;

public class PasswordValidator {
    public static boolean validate(String password) {
        if(password == null || password.length() < 8) {
            return false;
        }
        return true;
    }
}
