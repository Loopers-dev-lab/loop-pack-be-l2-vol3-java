package com.loopers.application.service;

import com.loopers.application.AuthenticationUseCase;
import com.loopers.domain.model.User;
import com.loopers.domain.model.UserId;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticationService implements AuthenticationUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void authenticate(UserId userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(rawPassword, user.getEncodedPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
    }
}