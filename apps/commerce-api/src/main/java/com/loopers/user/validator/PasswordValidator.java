package com.loopers.user.validator;

public class PasswordValidator {

    private PasswordValidator() {
    }

    public static void validate(String password, String birthDate) {
        if (password.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
        }
        if (password.length() > 16) {
            throw new IllegalArgumentException("비밀번호는 16자 이하여야 합니다.");
        }
        String pattern = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$";
        if (!password.matches(pattern)) {
            throw new IllegalArgumentException("비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.");
        }
        if (birthDate != null && password.contains(birthDate)) {
            throw new IllegalArgumentException("비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
