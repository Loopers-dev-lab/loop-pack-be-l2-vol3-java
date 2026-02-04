package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public User register(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        User.validateRawPassword(rawPassword, birthDate);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = new User(loginId, encodedPassword, name, birthDate, email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public User getUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String rawPassword) {
        User user = getUserByLoginId(loginId);
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    @Transactional
    public void changePassword(Long userId, String currentRawPassword, String newRawPassword) {
        User user = getUser(userId);

        if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        if (passwordEncoder.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }

        User.validateRawPassword(newRawPassword, user.getBirthDate());
        String newEncodedPassword = passwordEncoder.encode(newRawPassword);
        user.changePassword(newEncodedPassword);
    }
}
