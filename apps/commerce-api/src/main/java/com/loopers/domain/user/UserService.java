package com.loopers.domain.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String PASSWORD_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`]{8,16}$";

    public void signUp(SignUpCommand command) {
        if (userRepository.findByLoginId(command.loginId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        String birthDateString = command.birthDate().format(BIRTH_DATE_FORMATTER);
        validatePassword(command.password(), birthDateString);

        String encodedPassword = passwordEncoder.encode(command.password());

        User user = new User(
                command.loginId(),
                encodedPassword,
                command.name(),
                birthDateString,
                command.email()
        );
        userRepository.save(user);
    }

    private void validatePassword(String password, String birthDate) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문/숫자/특수문자만 가능합니다.");
        }
        if (password.contains(birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
