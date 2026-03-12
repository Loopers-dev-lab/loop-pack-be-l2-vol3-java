package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel save(UserModel userModel) {
        return userJpaRepository.save(userModel);
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return userJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdAndDeletedAtIsNull(loginId);
    }
}
