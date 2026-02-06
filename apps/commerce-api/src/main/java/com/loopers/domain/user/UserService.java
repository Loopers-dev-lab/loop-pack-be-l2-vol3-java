package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginIdValidator loginIdValidator;
    private final EmailValidator emailValidator;
    private final BirthDateValidator birthDateValidator;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String rawPassword) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    @Transactional
    public User register(String loginId, String rawPassword, String name, String email, String birthDate) {
        loginIdValidator.validate(loginId);
        emailValidator.validate(email);
        birthDateValidator.validate(birthDate);
        passwordPolicyValidator.validate(rawPassword, birthDate);

        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(loginId, encodedPassword, name, email, birthDate);

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }

        if (currentPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        passwordPolicyValidator.validate(newPassword, user.getBirthDate());

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.changePassword(encodedPassword);
    }
}
