package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.vo.UserId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<User> findByUserId(UserId userId) {
        return userJpaRepository.findByUserId(userId.value())
                .map(com.loopers.infrastructure.user.UserEntity::toDomain);
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        return userJpaRepository.existsByUserId(userId.value());
    }

    @Override
    public User save(User user) {
        if (!user.password().isEncoded()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "비밀번호가 암호화되지 않았습니다");
        }

        Optional<UserEntity> existingModel = userJpaRepository.findByUserId(user.id().value());

        if (existingModel.isPresent()) {
            UserEntity model = existingModel.get();
            model.updatePassword(user.password().value());
            return userJpaRepository.save(model).toDomain();
        }

        UserEntity newModel = com.loopers.infrastructure.user.UserEntity.from(user);
        return userJpaRepository.save(newModel).toDomain();
    }
}
