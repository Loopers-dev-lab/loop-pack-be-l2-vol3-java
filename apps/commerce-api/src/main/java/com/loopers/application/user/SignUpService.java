package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.SignUpValidator;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class SignUpService {
    private final SignUpValidator signUpValidator;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public void signUp(SignUpCommand command) {
        signUpValidator.validate(command);
        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(command, encodedPassword);

        userRepository.save(user);
    }
}
