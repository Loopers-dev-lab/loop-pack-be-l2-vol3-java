package com.loopers.domain.user;

import java.util.Optional;

/**
 * UserRepository 인터페이스 (Port)
 *
 * DIP 관점:
 * - 도메인 패키지에 위치 (고수준 모듈)
 * - 인프라스트럭처에 대한 의존성 없음 (순수 인터페이스)
 * - Infrastructure Layer의 UserJpaRepository가 구현 (Adapter)
 *
 * 이것이 진정한 DIP입니다:
 * - 도메인이 인프라를 전혀 모름
 * - 인프라가 도메인의 인터페이스를 구현
 * - 의존성 방향: Infrastructure → Domain
 */
public interface UserRepository {

    /**
     * 사용자를 저장합니다.
     *
     * @param user 저장할 User
     * @return 저장된 User
     */
    User save(User user);

    /**
     * ID로 사용자를 조회합니다.
     *
     * @param id 사용자 ID
     * @return 조회된 User (Optional)
     */
    Optional<User> findById(Long id);

    /**
     * 로그인 ID로 사용자를 조회합니다.
     *
     * @param loginId 로그인 ID
     * @return 조회된 User (Optional)
     */
    Optional<User> findByLoginId(String loginId);

    /**
     * 로그인 ID가 존재하는지 확인합니다.
     *
     * @param loginId 로그인 ID
     * @return 존재 여부
     */
    boolean existsByLoginId(String loginId);
}
