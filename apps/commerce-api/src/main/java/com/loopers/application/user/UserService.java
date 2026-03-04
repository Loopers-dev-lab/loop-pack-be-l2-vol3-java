package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Command

    @Transactional
    public User signUp(UserCommand.SignUp command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
        }

        User user = User.create(command.loginId(), command.rawPassword(), command.name(),
                command.birthDate(), command.email(), passwordEncoder);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(UserCommand.ChangePassword command) {
        User user = userRepository.findById(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
        user.changePassword(command.newRawPassword(), passwordEncoder);
    }

    // Query

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
    }

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String rawPassword) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다"));
        if (!user.matchesPassword(rawPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다");
        }
        return user;
    }
}
