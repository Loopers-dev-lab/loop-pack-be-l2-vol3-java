package com.loopers.infrastructure.address;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.common.vo.Address;
import org.springframework.stereotype.Component;

/**
 * UserAddressMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class UserAddressMapper {

    /**
     * Domain → JPA Entity
     */
    public UserAddressEntity toEntity(UserAddress domain) {
        UserAddressEntity entity = new UserAddressEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setReceiverName(domain.getReceiverName());
        entity.setPhone(domain.getPhone());

        // Address VO → 개별 필드 분해
        entity.setZipCode(domain.getZipCode());
        entity.setAddressLine1(domain.getAddressLine1());
        entity.setAddressLine2(domain.getAddressLine2());

        entity.setIsDefault(domain.isDefault());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setDeletedAt(domain.getDeletedAt());

        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public UserAddress toDomain(UserAddressEntity entity) {
        // 개별 필드 → Address VO 재조합
        Address address = new Address(
                entity.getZipCode(),
                entity.getAddressLine1(),
                entity.getAddressLine2()
        );

        return UserAddress.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getReceiverName(),
                entity.getPhone(),
                address,
                entity.getIsDefault(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
