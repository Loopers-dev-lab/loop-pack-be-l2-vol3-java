package com.loopers.domain.user;

import com.loopers.application.user.command.AuthenticateCommand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User authenticate(AuthenticateCommand command) {
        User user = userRepository.findByUserId(command.userId())
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

        if (!passwordEncoder.matches(command.rawPassword(), user.password().value())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        return user;
    }
}
