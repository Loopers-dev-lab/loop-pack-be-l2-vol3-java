package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자(User) 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드(save, findById, findAll, delete 등)가 자동 제공되며,
 * 메서드 이름 규칙 기반의 쿼리 메서드를 추가로 정의한다.</p>
 */
public interface UserJpaRepository extends JpaRepository<UserModel, String> {

    /**
     * 로그인 ID로 사용자를 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param loginId 로그인 ID
     * @return 사용자 (Optional)
     */
    Optional<UserModel> findByLoginId(String loginId);

    /**
     * 로그인 ID 존재 여부를 확인한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: EXISTS 쿼리가 자동 생성된다.</p>
     *
     * @param loginId 로그인 ID
     * @return 존재 여부
     */
    boolean existsByLoginId(String loginId);
}
