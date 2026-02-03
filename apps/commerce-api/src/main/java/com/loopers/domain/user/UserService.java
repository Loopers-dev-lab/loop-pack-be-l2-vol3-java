package com.loopers.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(String loginId, String password, String name, String birthDate, String email) {
        if (userRepository.existsByLoginId(new LoginId(loginId))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 로그인 ID입니다.");
        }

        User user = User.signUp(loginId, password, name, birthDate, email, passwordEncoder);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = getUser(userId);
        user.updatePassword(oldPassword, newPassword, passwordEncoder);
        userRepository.save(user);
    }
}
