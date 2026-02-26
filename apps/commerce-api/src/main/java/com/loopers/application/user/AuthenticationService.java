package com.loopers.application.user;

import com.loopers.application.user.AuthenticationUseCase;
import com.loopers.domain.model.user.User;
import com.loopers.domain.model.user.UserId;
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
   private static final String AUTH_FAILURE_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    @Override
    public void authenticate(UserId userId, String rawPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException(AUTH_FAILURE_MESSAGE));

        if (!passwordEncoder.matches(rawPassword, user.getEncodedPassword())) {
            throw new IllegalArgumentException(AUTH_FAILURE_MESSAGE);
        }
    }

}
