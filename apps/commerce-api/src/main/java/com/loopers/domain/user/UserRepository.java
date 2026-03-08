package com.loopers.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 도메인 리포지토리 인터페이스.
 */
public interface UserRepository {

    /**
     * 사용자를 저장한다.
     *
     * @param user 저장할 사용자
     * @return 저장된 사용자
     */
    User save(User user);

    /**
     * ID로 사용자를 조회한다.
     *
     * @param id 사용자 ID
     * @return 사용자 (존재하지 않으면 빈 Optional)
     */
    Optional<User> findById(Long id);

    /**
     * ID 목록에 해당하는 사용자를 모두 조회한다.
     *
     * @param ids 사용자 ID 목록
     * @return 사용자 목록
     */
    List<User> findAllByIdIn(List<Long> ids);

    /**
     * 로그인 ID로 사용자를 조회한다.
     *
     * @param loginId 로그인 ID
     * @return 사용자 (존재하지 않으면 빈 Optional)
     */
    Optional<User> findByLoginId(LoginId loginId);

    /**
     * 로그인 ID의 존재 여부를 확인한다.
     *
     * @param loginId 로그인 ID
     * @return 존재하면 true
     */
    boolean existsByLoginId(LoginId loginId);
}
