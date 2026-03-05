package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]*$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter YYYY_MM_DD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YY_MM_DD_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    @Transactional
    public User register(RegisterUserCommand command) {
        validateRegisterRequest(command.loginId(), command.password(), command.name(), command.birthDate(), command.email());

        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        String encryptedPassword = passwordEncoder.encode(command.password());
        User user = User.create(command.loginId(), encryptedPassword, command.name(), command.birthDate(), command.email());

        return userRepository.save(user);
    }

    public User getUserInfo(String loginId, String password) {
        return findUserAndValidatePassword(loginId, password);
    }

    @Transactional
    public void updatePassword(String loginId, String currentPassword, String newPassword) {
        User user = findUserAndValidatePassword(loginId, currentPassword);

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }

        validatePassword(newPassword, user.getBirthDate());

        String encryptedPassword = passwordEncoder.encode(newPassword);
        user.updatePassword(encryptedPassword);
    }

    private User findUserAndValidatePassword(String loginId, String password) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    private void validateRegisterRequest(String loginId, String password, String name, LocalDate birthDate, String email) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }

        validatePassword(password, birthDate);
        validateEmail(email);
    }

    private void validatePassword(String password, LocalDate birthDate) {
        if (password == null || password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }

        String yyyyMMdd = birthDate.format(YYYY_MM_DD_FORMATTER);
        String yyMMdd = birthDate.format(YY_MM_DD_FORMATTER);

        if (password.contains(yyyyMMdd) || password.contains(yyMMdd)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }
}
