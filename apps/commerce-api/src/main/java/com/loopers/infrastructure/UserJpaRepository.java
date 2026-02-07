package com.loopers.infrastructure;

import com.loopers.domain.user.UserModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {

    Boolean existsByEmail(String email);

    Boolean existsByLoginId(String loginId);

    Optional<UserModel> findByLoginId(String loginId);
}
