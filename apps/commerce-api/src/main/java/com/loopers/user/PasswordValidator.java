package com.loopers.user;

public class PasswordValidator {

    // 영문 대소문자
    private static final String ALPHABET = "a-zA-Z";
    // 숫자
    private static final String NUMBER = "0-9";
    // 특수문자
    private static final String SPECIAL_CHAR = "!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?";
    // 허용 문자 패턴
    private static final String ALLOWED_CHARS_PATTERN = "^[" + ALPHABET + NUMBER + SPECIAL_CHAR + "]+$";

    public static boolean validate(String password) {
        if (password == null) {
            return false;
        }
        if (password.length() < 8) {
            return false;
        }
        if(password.length() > 16) {
            return false;
        }
        if(!password.matches(ALLOWED_CHARS_PATTERN)) {
            return false;
        }
        return true;
    }
}
