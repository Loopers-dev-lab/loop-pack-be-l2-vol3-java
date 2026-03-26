package com.loopers.infrastructure.usercard;

import com.loopers.domain.usercard.UserCard;
import com.loopers.domain.usercard.UserCardRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserCardRepositoryImpl implements UserCardRepository {

    private final UserCardJpaRepository userCardJpaRepository;

    @Override
    public UserCard save(UserCard userCard) {
        return userCardJpaRepository.save(userCard);
    }

    @Override
    public Optional<UserCard> findById(Long id) {
        return userCardJpaRepository.findById(id);
    }

    @Override
    public Optional<UserCard> findDefaultByUserId(Long userId) {
        return userCardJpaRepository.findByUserIdAndIsDefaultTrue(userId);
    }

    @Override
    public List<UserCard> findAllByUserId(Long userId) {
        return userCardJpaRepository.findAllByUserId(userId);
    }
}