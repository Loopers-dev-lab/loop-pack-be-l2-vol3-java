package com.loopers.domain.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {
    UserModel save(UserModel userModel);

    Optional<UserModel> findById(Long id);

    Optional<UserModel> findByLoginId(String loginId);

    Page<UserModel> findAll(Pageable pageable);

    List<UserModel> findAll();
}
