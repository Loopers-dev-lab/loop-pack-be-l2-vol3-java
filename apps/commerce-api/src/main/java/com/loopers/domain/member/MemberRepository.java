package com.loopers.domain.member;

import com.loopers.domain.member.vo.MemberId;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {

    Optional<Member> findByMemberId(MemberId memberId);

    Optional<UUID> findDbIdByMemberId(MemberId memberId);

    boolean existsByMemberId(MemberId memberId);

    Member save(Member member);
}
