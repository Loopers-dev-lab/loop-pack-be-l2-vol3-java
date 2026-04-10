package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel save(UserModel userModel) {
        return userJpaRepository.save(userModel);
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return userJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdAndDeletedAtIsNull(loginId);
    }

    @Override
    public Page<UserModel> findAll(Pageable pageable) {
        return userJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public List<UserModel> findAll() {
        return userJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public int deductPoint(Long id, long amount) {
        return userJpaRepository.deductPoint(id, amount);
    }
}
