package com.loopers.infrastructure.persistence.jpa.useraction;

import com.loopers.domain.useraction.UserActionLog;

/**
 * UserActionLog 도메인 객체와 JPA 엔티티 간 변환을 담당.
 */
public class UserActionLogMapper {

    private UserActionLogMapper() {}

    /**
     * JPA 엔티티를 도메인 객체로 변환.
     */
    public static UserActionLog toDomain(UserActionLogJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserActionLog.reconstitute(
            entity.getId(),
            entity.getUserId(),
            entity.getActionType(),
            entity.getTargetId(),
            entity.getOccurredAt(),
            entity.getCreatedAt()
        );
    }

    /**
     * 도메인 객체를 JPA 엔티티로 변환 (신규 저장용).
     */
    public static UserActionLogJpaEntity toJpaEntity(UserActionLog domain) {
        if (domain == null) {
            return null;
        }
        return new UserActionLogJpaEntity(
            domain.getUserId(),
            domain.getActionType(),
            domain.getTargetId(),
            domain.getOccurredAt()
        );
    }
}
