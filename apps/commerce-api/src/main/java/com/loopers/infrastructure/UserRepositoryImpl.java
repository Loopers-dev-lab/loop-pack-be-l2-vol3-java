package com.loopers.infrastructure;

import com.loopers.domain.UserModel;
import com.loopers.domain.UserRepository;
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
    public Boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }
}
