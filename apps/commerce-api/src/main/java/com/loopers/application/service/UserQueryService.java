package com.loopers.application.service;

import com.loopers.application.UserQueryUseCase;
import com.loopers.domain.model.User;
import com.loopers.domain.model.UserId;
import com.loopers.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueryService implements UserQueryUseCase {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserInfoResponse getUserInfo(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return new UserInfoResponse(
                user.getUserId().getValue(),
                maskName(user.getUserName().getValue()),
                user.getBirth().getValue(),
                user.getEmail().getValue()
        );
    }

    private String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
