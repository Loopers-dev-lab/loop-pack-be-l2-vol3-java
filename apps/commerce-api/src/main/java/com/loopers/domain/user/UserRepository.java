package com.loopers.domain.user;

import java.util.Optional;

/**
 * 사용자 도메인 리포지토리 인터페이스.
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의하며, 인프라스트럭처 계층에서 구현한다.
 */
public interface UserRepository {

    /**
     * 사용자 엔티티를 저장한다.
     *
     * @param user 저장할 사용자 엔티티
     * @return 저장된 사용자 엔티티
     */
    UserModel save(UserModel user);

    /**
     * 사용자 ID(UUID)로 사용자를 조회한다.
     *
     * @param userId 사용자 UUID
     * @return 사용자 (Optional)
     */
    Optional<UserModel> findByUserId(String userId);

    /**
     * 로그인 ID로 사용자를 조회한다.
     *
     * @param loginId 로그인 ID
     * @return 사용자 (Optional)
     */
    Optional<UserModel> findByLoginId(String loginId);

    /**
     * 로그인 ID의 존재 여부를 확인한다.
     *
     * @param loginId 로그인 ID
     * @return 존재하면 true
     */
    boolean existsByLoginId(String loginId);
}
