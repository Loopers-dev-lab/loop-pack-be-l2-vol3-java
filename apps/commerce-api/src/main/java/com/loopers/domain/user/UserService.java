package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.security.PasswordEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional
    public User register(String loginId, String password, String name, LocalDate birthDate, String email, Gender gender) {
        // 중복 ID 체크
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID입니다");
        }

        // User 생성 및 저장
        User user = User.register(loginId, password, name, birthDate, email, gender, passwordEncryptor);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserInfo(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        try {
            user.changePassword(oldPassword, newPassword, passwordEncryptor);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }
}
