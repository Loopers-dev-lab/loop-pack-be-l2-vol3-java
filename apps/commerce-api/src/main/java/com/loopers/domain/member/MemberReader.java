package com.loopers.domain.member;

public interface MemberReader {
    boolean existsByLoginId(String loginId);
}
