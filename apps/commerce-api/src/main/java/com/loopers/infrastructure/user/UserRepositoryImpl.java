package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.infrastructure.support.ConstraintViolationHelper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        try {
            return userJpaRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolationHelper.isUniqueViolation(e, "uk_users_login_id")) {
                throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
            }
            throw e;
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginId(loginId);
    }
}
