package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Member save(Member member) {
        MemberEntity entity = MemberEntity.from(member);
        MemberEntity saved = memberJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return memberJpaRepository.existsByLoginId(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return memberJpaRepository.existsByEmail(email);
    }

    @Override
    public Optional<Member> findByLoginId(String loginId) {
        return memberJpaRepository.findByLoginId(loginId)
            .map(MemberEntity::toDomain);
    }

    @Override
    @Transactional
    public void updatePassword(String loginId, String encodedPassword) {
        MemberEntity entity = memberJpaRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));
        entity.updatePassword(encodedPassword);
    }
}