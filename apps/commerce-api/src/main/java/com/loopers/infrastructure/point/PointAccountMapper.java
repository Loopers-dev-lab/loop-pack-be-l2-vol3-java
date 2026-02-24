package com.loopers.infrastructure.point;

import com.loopers.domain.point.PointAccount;
import org.springframework.stereotype.Component;

/**
 * PointAccountMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class PointAccountMapper {

    /**
     * Domain → JPA Entity
     */
    public PointAccountEntity toEntity(PointAccount pointAccount) {
        PointAccountEntity entity = new PointAccountEntity();
        entity.setId(pointAccount.getId());
        entity.setUserId(pointAccount.getUserId());
        entity.setBalance(pointAccount.getBalance());
        entity.setCreatedAt(pointAccount.getCreatedAt());
        entity.setUpdatedAt(pointAccount.getUpdatedAt());
        entity.setDeletedAt(pointAccount.getDeletedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public PointAccount toDomain(PointAccountEntity entity) {
        return PointAccount.reconstitute(
            entity.getId(),
            entity.getUserId(),
            entity.getBalance(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt()
        );
    }
}
