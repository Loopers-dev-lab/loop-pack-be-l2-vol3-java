package com.loopers.user;

public class PasswordValidator {
    public static boolean validate(String password) {
        if (password == null) {
            return false;
        } else if (password.length() < 8) {
            return false;
        } else if (password.length() > 16) {
            return false;
        }
        return true;
    }
}
