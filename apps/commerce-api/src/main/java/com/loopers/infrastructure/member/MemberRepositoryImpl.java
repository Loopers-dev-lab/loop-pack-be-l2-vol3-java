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
            return memberJpaRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 회원 정보입니다.", e);
        }
    }

    @Override
    public Optional<Member> findById(Long id) {
        return memberJpaRepository.findById(id);
    }

    @Override
    public Optional<Member> findByMemberIdValue(String memberIdValue) {
        return memberJpaRepository.findByMemberIdValue(memberIdValue);
    }

    @Override
    public boolean existsByMemberIdValue(String memberIdValue) {
        return memberJpaRepository.existsByMemberIdValue(memberIdValue);
    }
}
