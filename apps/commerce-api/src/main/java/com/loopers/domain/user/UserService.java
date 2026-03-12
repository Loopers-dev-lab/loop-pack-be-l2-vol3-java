package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+=-])[A-Za-z\\d~!@#$%^&*()_+=-]{8,16}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel signup(String loginId, String rawPassword, String name, String birthDate, String email) {
        if (userRepository.findByLoginId(loginId).isPresent()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 아이디입니다.");
        }

        LocalDate parsedBirthDate = LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
        validatePasswordFormat(rawPassword);
        validateBirthDateNotInPassword(rawPassword, parsedBirthDate);

        return userRepository.save(
                UserModel.create(loginId, passwordEncoder.encode(rawPassword), name, parsedBirthDate, email));
    }

    @Transactional(readOnly = true)
    public UserModel getByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public void changePassword(String loginId, String rawCurrentPassword, String rawNewPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(rawCurrentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(rawNewPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 사용 중인 비밀번호는 사용할 수 없습니다.");
        }

        validatePasswordFormat(rawNewPassword);
        validateBirthDateNotInPassword(rawNewPassword, user.getBirthDate());

        user.changePassword(passwordEncoder.encode(rawNewPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deductPoint(Long userId, long amount) {
        UserModel user = userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        user.deductPoint(amount);
    }

    private void validatePasswordFormat(String rawPassword) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자 조합이어야 합니다.");
        }
    }

    private void validateBirthDateNotInPassword(String rawPassword, LocalDate birthDate) {
        if (rawPassword.contains(birthDate.format(BIRTH_DATE_FORMATTER))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비밀번호 내에 포함될 수 없습니다.");
        }
    }
}
