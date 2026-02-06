package com.loopers.domain.member;

import java.util.Optional;

/**
 * 회원 Repository 인터페이스
 *
 * Domain Layer에서 정의하고 Infrastructure Layer에서 구현
 * (DIP - Dependency Inversion Principle)
 */
public interface MemberRepository {

    /**
     * 회원 저장
     *
     * @param member 저장할 회원
     * @return 저장된 회원
     */
    MemberModel save(MemberModel member);

    /**
     * 로그인 ID로 회원 조회
     *
     * @param loginId 로그인 ID
     * @return 회원 (Optional)
     */
    Optional<MemberModel> findByLoginId(String loginId);

    /**
     * 로그인 ID 존재 여부 확인
     *
     * @param loginId 로그인 ID
     * @return 존재 여부
     */
    boolean existsByLoginId(String loginId);
}
