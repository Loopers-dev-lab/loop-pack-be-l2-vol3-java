package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Member save(Member member) {
        try {
            if (member.getId() == null) {
                MemberJpaEntity entity = MemberJpaEntity.from(member);
                MemberJpaEntity saved = memberJpaRepository.save(entity);
                return saved.toDomain();
            }

            MemberJpaEntity entity = memberJpaRepository.findById(member.getId())
                    .orElseThrow(() -> new IllegalStateException("Member not found: " + member.getId()));
            entity.update(member);
            return entity.toDomain();
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 회원 정보입니다.", e);
        }
    }

    @Override
    public Optional<Member> findByMemberIdValue(String memberIdValue) {
        return memberJpaRepository.findByMemberId(memberIdValue)
                .map(MemberJpaEntity::toDomain);
    }

    @Override
    public boolean existsByMemberIdValue(String memberIdValue) {
        return memberJpaRepository.existsByMemberId(memberIdValue);
    }
}
