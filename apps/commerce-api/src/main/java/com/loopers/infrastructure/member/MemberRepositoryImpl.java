package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Optional<Member> findByMemberId(MemberId memberId) {
        return memberJpaRepository.findByMemberId(memberId.value())
                .map(com.loopers.infrastructure.member.MemberEntity::toDomain);
    }

    @Override
    public Optional<Long> findDbIdByMemberId(MemberId memberId) {
        return memberJpaRepository.findByMemberId(memberId.value())
                .map(com.loopers.infrastructure.member.MemberEntity::getId);
    }

    @Override
    public boolean existsByMemberId(MemberId memberId) {
        return memberJpaRepository.existsByMemberId(memberId.value());
    }

    @Override
    public Member save(Member member) {
        if (!member.password().isEncoded()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "비밀번호가 암호화되지 않았습니다");
        }

        Optional<MemberEntity> existingModel = memberJpaRepository.findByMemberId(member.id().value());

        if (existingModel.isPresent()) {
            MemberEntity model = existingModel.get();
            model.updateFrom(member);
            return memberJpaRepository.save(model).toDomain();
        }

        MemberEntity newModel = com.loopers.infrastructure.member.MemberEntity.from(member);
        return memberJpaRepository.save(newModel).toDomain();
    }
}
