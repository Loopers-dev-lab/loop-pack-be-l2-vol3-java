package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MemberRepository 구현체
 *
 * Domain Layer의 인터페이스를 Infrastructure Layer에서 구현
 * (DIP - Dependency Inversion Principle)
 *
 * Spring Data JPA를 위임(Delegation) 패턴으로 사용
 */
@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository jpaRepository;

    /**
     * 회원 저장
     *
     * @param member 저장할 회원
     * @return 저장된 회원 (ID가 자동 생성됨)
     */
    @Override
    public MemberModel save(MemberModel member) {
        return jpaRepository.save(member);
    }

    /**
     * 로그인 ID로 회원 조회
     *
     * @param loginId 로그인 ID
     * @return 회원 (Optional)
     */
    @Override
    public Optional<MemberModel> findByLoginId(String loginId) {
        return jpaRepository.findByLoginId(loginId);
    }

    /**
     * 로그인 ID 존재 여부 확인
     *
     * @param loginId 로그인 ID
     * @return 존재 여부
     */
    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginId(loginId);
    }
}
