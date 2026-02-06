package com.loopers.infrastructure;

import com.loopers.domain.model.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.infrastructure.entity.UserJpaEntity;
import com.loopers.infrastructure.repository.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    public UserRepositoryImpl( UserJpaRepository userJpaRepository) {this.userJpaRepository = userJpaRepository;}

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        UserJpaEntity saved = userJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        return userJpaRepository.findByUserId(userId.getValue())
                .map(this::toDomain);
    }

    @Override
    public boolean existsById(UserId userId) {
        return userJpaRepository.existsByUserId(userId.getValue());
    }

    private UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.getId(),
                user.getUserId(),
                user.getEncodedPassword(),
                user.getUserName(),
                user.getBirth(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }

    private User toDomain(UserJpaEntity entity) {
        return User.reconstitute(
                entity.getId(),
                UserId.of(entity.getUserId()),
                UserName.of(entity.getUsername()),
                entity.getEncodedPassword(),
                Birthday.of(entity.getBirthday()),
                Email.of(entity.getEmail()),
                WrongPasswordCount.init(),
                entity.getCreatedAt()
        );
    }

}
