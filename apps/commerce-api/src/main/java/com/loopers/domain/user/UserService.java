package com.loopers.domain.user;

import com.loopers.interfaces.api.user.dto.CreateUserRequestV1;
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

    public void signUp(CreateUserRequestV1 request) {
        if (userRepository.findByLoginId(request.getLoginId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        String birthDateString = request.getBirthDate().format(BIRTH_DATE_FORMATTER);
        validatePassword(request.getPassword(), birthDateString);

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getLoginId(),
                encodedPassword,
                request.getName(),
                birthDateString,
                request.getEmail()
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
