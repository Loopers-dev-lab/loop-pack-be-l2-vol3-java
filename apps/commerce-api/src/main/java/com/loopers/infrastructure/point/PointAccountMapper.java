package com.loopers.infrastructure.point;

import com.loopers.domain.point.PointAccount;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

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
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(pointAccount.getCreatedAt() != null ? pointAccount.getCreatedAt() : now);
        entity.setUpdatedAt(now);
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
