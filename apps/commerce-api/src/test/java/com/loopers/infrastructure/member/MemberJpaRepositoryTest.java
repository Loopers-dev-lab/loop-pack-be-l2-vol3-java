package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA Repository
 *
 * JpaRepository를 상속받으면 기본 CRUD 메서드가 자동 제공됨:
 * - save()
 * - findById()
 * - findAll()
 * - delete()
 * - count()
 * 등
 */
public interface MemberJpaRepositoryTest extends JpaRepository<MemberModel, Long> {

    /**
     * 로그인 ID로 회원 조회
     *
     * Spring Data JPA가 메서드 이름을 분석해서 자동으로 쿼리 생성:
     * SELECT * FROM members WHERE login_id = ?
     *
     * @param loginId 로그인 ID
     * @return 회원 (Optional)
     */
    Optional<MemberModel> findByLoginId(String loginId);

    /**
     * 로그인 ID 존재 여부 확인
     *
     * Spring Data JPA가 자동으로 쿼리 생성:
     * SELECT COUNT(*) > 0 FROM members WHERE login_id = ?
     *
     * @param loginId 로그인 ID
     * @return 존재 여부
     */
    boolean existsByLoginId(String loginId);
}
