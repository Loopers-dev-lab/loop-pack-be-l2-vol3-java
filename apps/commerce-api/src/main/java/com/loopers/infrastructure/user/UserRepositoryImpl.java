package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 도메인 {@link UserRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link UserJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    /**
     * 사용자를 저장한다.
     *
     * @param user 저장할 사용자 엔티티
     * @return 저장된 사용자 엔티티 (ID가 자동 생성됨)
     */
    @Override
    public UserModel save(UserModel user) {
        return jpaRepository.save(user);
    }

    /**
     * 사용자 ID로 사용자를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 사용자 (Optional)
     */
    @Override
    public Optional<UserModel> findByUserId(Long userId) {
        return jpaRepository.findById(userId);
    }

    /**
     * 로그인 ID로 사용자를 조회한다.
     *
     * @param loginId 로그인 ID
     * @return 사용자 (Optional)
     */
    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return jpaRepository.findByLoginId(loginId);
    }

    /**
     * 로그인 ID 존재 여부를 확인한다.
     *
     * @param loginId 로그인 ID
     * @return 존재 여부
     */
    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginId(loginId);
    }
}
