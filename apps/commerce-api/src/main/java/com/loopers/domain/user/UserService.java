package com.loopers.domain.user;

import com.loopers.domain.user.policy.PasswordPolicy;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Transactional
    public UserModel signup(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        passwordPolicy.validateForSignup(rawPassword, birthDate);

        String encodedPassword = passwordEncoder.encode(rawPassword);
        UserModel user = new UserModel(loginId, encodedPassword, name, birthDate, email);

        return userRepository.save(user);
    }
}