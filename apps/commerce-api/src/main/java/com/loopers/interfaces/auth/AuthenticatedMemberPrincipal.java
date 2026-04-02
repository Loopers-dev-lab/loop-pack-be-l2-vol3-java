package com.loopers.interfaces.auth;

import com.loopers.domain.member.Member;

public record AuthenticatedMemberPrincipal(Member member) {
}
