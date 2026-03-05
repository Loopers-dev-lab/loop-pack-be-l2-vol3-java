package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    Optional<Member> findByMemberIdValue(String memberIdValue);

    boolean existsByMemberIdValue(String memberIdValue);

    Optional<Member> findByIdWithLock(Long id);
}
