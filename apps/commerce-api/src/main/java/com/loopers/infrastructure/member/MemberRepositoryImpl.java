package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * MemberRepository 구현체
 * 
 * Domain(Member) ↔ Entity(MemberEntity) 변환을 담당합니다.
 * 도메인 레이어는 MemberRepository 인터페이스만 알고,
 * 인프라 레이어의 JPA 세부 구현은 모릅니다.
 */
@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Member save(Member member) {
        MemberEntity entity;

        if (member.getId() != null) {
            // 기존 엔티티 업데이트 (ID가 있는 경우)
            entity = memberJpaRepository.findById(member.getId())
                    .orElseGet(() -> MemberEntity.from(member));
            entity.changePassword(member.getPassword()); // 변경 가능한 필드 업데이트
        } else {
            // 신규 엔티티 생성
            entity = MemberEntity.from(member);
        }

        // JPA 저장
        MemberEntity savedEntity = memberJpaRepository.save(entity);

        // Entity → Domain 변환
        return savedEntity.toMember();
    }

    @Override
    public Optional<Member> findByLoginId(String loginId) {
        return memberJpaRepository.findByLoginId(loginId)
                .map(MemberEntity::toMember); // Entity → Domain 변환
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return memberJpaRepository.existsByLoginId(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return memberJpaRepository.existsByEmail(email);
    }

    /**
     * 비밀번호 변경을 위한 메서드
     * 도메인 객체의 변경사항을 영속성에 반영합니다.
     */
    public void updatePassword(Member member) {
        memberJpaRepository.findByLoginId(member.getLoginId())
                .ifPresent(entity -> {
                    entity.changePassword(member.getPassword());
                    memberJpaRepository.save(entity);
                });
    }
}
