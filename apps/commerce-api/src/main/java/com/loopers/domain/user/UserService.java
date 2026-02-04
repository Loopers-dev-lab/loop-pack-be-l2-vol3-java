package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(String loginId, String rawPassword, String name, String birthday, String email) {
        userRepository.findByLoginId(loginId).ifPresent(u -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID입니다.");
        });

        User.validateRawPassword(rawPassword, birthday);

        String encryptedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(loginId, encryptedPassword, name, birthday, email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId, String rawPassword) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        return UserInfo.fromWithMaskedName(user);
    }
}
