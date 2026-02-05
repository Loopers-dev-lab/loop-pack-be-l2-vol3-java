package com.loopers.domain.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class SignUpValidator {
    private final UserRepository userRepository;

    public void validate(SignUpCommand command) {
        if (userRepository.findByLoginId(command.loginId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        if (command.birthDate().isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.");
        }

        PasswordPolicyValidator.validate(command.password(), command.birthDate());
    }
}
