package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional
    public User signup(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        PasswordPolicy.validate(rawPassword, birthDate);

        String encryptedPassword = passwordEncryptor.encrypt(rawPassword);
        User user = new User(loginId, encryptedPassword, name, birthDate, email);

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, String currentRawPassword, String newRawPassword) {
        if (!passwordEncryptor.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호가 올바르지 않습니다.");
        }
        if (passwordEncryptor.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }

        PasswordPolicy.validate(newRawPassword, user.getBirthDate());

        String encryptedPassword = passwordEncryptor.encrypt(newRawPassword);
        user.changePassword(encryptedPassword);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String rawPassword) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncryptor.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다.");
        }

        return user;
    }
}
