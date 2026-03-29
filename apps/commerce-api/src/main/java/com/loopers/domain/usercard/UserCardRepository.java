package com.loopers.domain.usercard;

import java.util.List;
import java.util.Optional;

public interface UserCardRepository {

    UserCard save(UserCard userCard);

    Optional<UserCard> findById(Long id);

    Optional<UserCard> findDefaultByUserId(Long userId);

    List<UserCard> findAllByUserId(Long userId);
}