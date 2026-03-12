package com.loopers.infrastructure.persistence.jpa.like;

import com.loopers.domain.like.Like;

/**
 * Like 도메인 객체와 JPA 엔티티 간 변환을 담당.
 */
public class LikeMapper {

    private LikeMapper() {}

    /**
     * JPA 엔티티를 도메인 객체로 변환.
     */
    public static Like toDomain(LikeJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Like.reconstitute(
            entity.getId(),
            entity.getUserId(),
            entity.getProductId(),
            entity.getCreatedAt()
        );
    }

    /**
     * 도메인 객체를 JPA 엔티티로 변환 (신규 저장용).
     */
    public static LikeJpaEntity toJpaEntity(Like domain) {
        if (domain == null) {
            return null;
        }
        return new LikeJpaEntity(
            domain.getUserId(),
            domain.getProductId()
        );
    }
}
