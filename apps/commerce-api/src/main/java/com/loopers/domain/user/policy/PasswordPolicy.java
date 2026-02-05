package com.loopers.domain.user.policy;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class PasswordPolicy {

    /**
     * 회원가입 시 비밀번호 정책
     */
    public void validateForSignup(String rawPassword, LocalDate birthDate) {
        validateCommon(rawPassword, birthDate);
    }

    /**
     * 비밀번호 변경 시 비밀번호 정책
     * - 공통 정책 + (현재 비밀번호와 달라야 함)
     */
    public void validateForChange(String currentRawPassword,
                                  String newRawPassword,
                                  LocalDate birthDate) {
        validateCommon(newRawPassword, birthDate);
        validateDifferentFromCurrent(currentRawPassword, newRawPassword);
    }

    /**
     * 변경 정책: 새 비밀번호는 현재 비밀번호와 달라야 한다.
     */
    private void validateDifferentFromCurrent(String currentRawPassword, String newRawPassword) {
        if (currentRawPassword == null || currentRawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호는 비어있을 수 없습니다.");
        }
        if (newRawPassword == null || newRawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 비어있을 수 없습니다.");
        }
        if (currentRawPassword.equals(newRawPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
    }

    /**
     * 공통 비밀번호 정책 (회원가입/변경 모두 적용)
     */
    public void validateCommon(String rawPassword, LocalDate birthDate) {
        validateNotNullAndLength(rawPassword);
        validateAllowedCharacters(rawPassword);
        validateNotContainsBirthDate(rawPassword, birthDate);
    }

    private void validateNotNullAndLength(String password) {
        if (password == null || password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
    }

    private void validateAllowedCharacters(String password) {
        if (!password.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?`~\\\\]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
    }

    private void validateNotContainsBirthDate(String password, LocalDate birthDate) {
        String birthDateStr = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        if (password.contains(birthDateStr)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}