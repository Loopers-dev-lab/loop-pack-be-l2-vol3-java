package com.loopers.infrastructure.usercard;

import com.loopers.domain.usercard.UserCard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCardJpaRepository extends JpaRepository<UserCard, Long> {

    Optional<UserCard> findByUserIdAndIsDefaultTrue(Long userId);

    List<UserCard> findAllByUserId(Long userId);
}