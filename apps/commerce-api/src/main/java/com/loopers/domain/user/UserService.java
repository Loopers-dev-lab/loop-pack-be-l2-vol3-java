package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User register(String loginId, String password, String name, String birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }
        User user = new User(loginId, password, name, birthDate, email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND, "[loginId = " + loginId + "] 사용자를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String password) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND, "존재하지 않는 사용자입니다."));

        if (!user.matchPassword(password)) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH, "비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        User user = authenticate(loginId, currentPassword);
        user.changePassword(newPassword);
    }
}
