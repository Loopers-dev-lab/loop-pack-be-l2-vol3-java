package com.loopers.domain.user;

import com.loopers.application.user.SignUpCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final SignUpValidator signUpValidator;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public void signUp(SignUpCommand command) {
        signUpValidator.validate(command);

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(
                command.loginId(),
                encodedPassword,
                command.name(),
                command.birthDate(),
                command.email()
        );

        userRepository.save(user);
    }
}
