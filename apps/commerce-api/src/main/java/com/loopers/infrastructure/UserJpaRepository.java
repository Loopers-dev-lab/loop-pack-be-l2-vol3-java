package com.loopers.infrastructure;

import com.loopers.domain.UserModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {

    Boolean existsByEmail(String email);

    Optional<UserModel> findByLoginId(String loginId);
}
