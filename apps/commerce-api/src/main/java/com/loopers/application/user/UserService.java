package com.loopers.application.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResult signUp(String loginId, String password, String name, String birthDate, String email) {
        if (userRepository.existsByLoginId(new LoginId(loginId))) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }

        try {
            User user = User.signUp(loginId, password, name, birthDate, email, passwordEncoder);
            User savedUser = userRepository.save(user);
            return UserResult.from(savedUser);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }
    }

    @Transactional(readOnly = true)
    public UserResult getMyInfo(Long userId) {
        User user = getUser(userId);
        return UserResult.from(user);
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