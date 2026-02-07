package com.loopers.domain.member;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakeMemberRepository implements MemberRepository {

    private final Map<String, Member> store = new HashMap<>();

    @Override
    public Optional<Member> findByLoginId(String loginId) {
        return Optional.ofNullable(store.get(loginId));
    }

    @Override
    public Member save(Member member) {
        store.put(member.getLoginId(), member);
        return member;
    }
}
