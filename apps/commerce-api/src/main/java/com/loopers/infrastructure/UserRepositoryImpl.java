package com.loopers.infrastructure;

import com.loopers.domain.LoginId;
import com.loopers.domain.UserModel;
import com.loopers.domain.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel save(UserModel userModel) {
        return userJpaRepository.save(userModel);
    }

    @Override
    public Optional<UserModel> find(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId);
    }
}
