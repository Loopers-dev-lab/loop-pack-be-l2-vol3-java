package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.PasswordPolicyValidator;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserApplicationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void updatePassword(UpdatePasswordCommand command) {
        User user = getUserById(command.userId());

        if (!passwordEncoder.matches(command.currentPassword(), user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        if (passwordEncoder.matches(command.newPassword(), user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }

        PasswordPolicyValidator.validate(command.newPassword(), user.getBirthDate());
        String encoded = passwordEncoder.encode(command.newPassword());
        user.updatePassword(encoded);
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(Long userId) {
        User user = getUserById(userId);
        return UserInfo.from(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String loginPw) {
        User user = getUserByLoginId(loginId);
        if (!passwordEncoder.matches(loginPw, user.getPassword())) {
            throw new CoreException(ErrorType.NOT_FOUND, "사용자 정보가 올바르지 않습니다.");
        }

        return user;
    }

    private User getUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                             .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                                                  "[loginId = " + loginId + "] 를 찾을 수 없습니다."));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                             .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                                                  "[userId = " + userId + "] 를 찾을 수 없습니다."));
    }
}
