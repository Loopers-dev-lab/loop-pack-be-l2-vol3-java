package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.loginId.value = :loginId AND u.deletedAt IS NULL")
    Optional<User> findByLoginId(@Param("loginId") String loginId);
}
