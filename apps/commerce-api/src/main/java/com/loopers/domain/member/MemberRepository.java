package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
    MemberModel save(MemberModel memberModel);
    Optional<MemberModel> findByLoginId(String id);
    Optional<MemberModel> update(MemberModel memberModel);

}
