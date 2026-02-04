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

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                                  .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 를 찾을 수 없습니다."));

        return UserInfo.from(user);
    }
}
