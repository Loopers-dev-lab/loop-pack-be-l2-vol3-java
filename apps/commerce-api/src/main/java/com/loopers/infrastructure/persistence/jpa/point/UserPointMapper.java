package com.loopers.infrastructure.persistence.jpa.point;

import com.loopers.domain.point.UserPoint;

/**
 * UserPoint Domain ↔ JPA Entity 변환 Mapper.
 */
public class UserPointMapper {

    private UserPointMapper() {}

    public static UserPoint toDomain(UserPointJpaEntity entity) {
        return UserPoint.reconstitute(
            entity.getId(),
            entity.getUserId(),
            entity.getBalance(),
            entity.getUpdatedAt()
        );
    }

    public static UserPointJpaEntity toJpaEntity(UserPoint domain) {
        return new UserPointJpaEntity(
            domain.getUserId(),
            domain.getBalance()
        );
    }

    public static void updateJpaEntity(UserPointJpaEntity entity, UserPoint domain) {
        entity.setBalance(domain.getBalance());
    }
}
