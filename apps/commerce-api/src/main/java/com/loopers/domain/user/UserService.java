package com.loopers.domain.user;

import com.loopers.domain.user.policy.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public UserModel signup(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}