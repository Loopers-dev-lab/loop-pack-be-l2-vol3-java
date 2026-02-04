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
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }

        User user = User.signUp(loginId, password, name, birthDate, email, passwordEncoder);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
    }

    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = getUser(userId);
        user.updatePassword(oldPassword, newPassword, passwordEncoder);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Long login(String loginId, String loginPw) {
        if (loginId == null || loginPw == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        User user = userRepository.findByLoginId(new LoginId(loginId))
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));
        user.verifyPassword(loginPw, passwordEncoder);

        return user.getId();
    }
}
