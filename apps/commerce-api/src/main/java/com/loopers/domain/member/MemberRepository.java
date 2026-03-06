package com.loopers.domain.member;

import com.loopers.domain.member.vo.MemberId;

import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findByMemberId(MemberId memberId);

    Optional<Long> findDbIdByMemberId(MemberId memberId);

    boolean existsByMemberId(MemberId memberId);

    Member save(Member member);
}
