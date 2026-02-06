package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void updatePassword(UpdatePasswordCommand command) {
        User user = getUser(command.loginId());

        if (passwordEncoder.matches(command.newPassword(), user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }

        PasswordPolicyValidator.validate(command.newPassword(), user.getBirthDate());
        String encoded = passwordEncoder.encode(command.newPassword());
        user.updatePassword(encoded);
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId) {
        User user = getUser(loginId);
        return UserInfo.from(user);
    }

    private User getUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                             .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                                                  "[loginId = " + loginId + "] 를 찾을 수 없습니다."));
    }
}
