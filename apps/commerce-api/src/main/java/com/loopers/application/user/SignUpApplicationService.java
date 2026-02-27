package com.loopers.application.user;

import com.loopers.domain.user.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class SignUpApplicationService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public void signUp(SignUpCommand command) {
        if (userRepository.findByLoginId(command.loginId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        PasswordPolicyValidator.validate(command.password(), command.birthDate());
        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(command, encodedPassword);

        userRepository.save(user);
    }
}
