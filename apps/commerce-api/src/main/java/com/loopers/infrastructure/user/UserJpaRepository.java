package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {
    Optional<UserModel> findByLoginId(String loginId);
    Optional<UserModel> findByLoginIdAndDeletedAtIsNull(String loginId);
    Optional<UserModel> findByIdAndDeletedAtIsNull(Long id);
}
