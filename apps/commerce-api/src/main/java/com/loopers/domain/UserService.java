package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel save(UserModel userModel) {
        return userRepository.save(userModel);
    }

    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다."));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
        return user;
    }

    public Boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
