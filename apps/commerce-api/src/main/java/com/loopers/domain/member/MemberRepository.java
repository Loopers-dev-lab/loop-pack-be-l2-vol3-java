package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
	MemberModel save(MemberModel member);

	Optional<MemberModel> findByLoginId(String loginId);
}
