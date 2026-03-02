package com.loopers.infrastructure.address;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * UserAddressRepository 구현체
 * Mapper를 활용하여 Domain ↔ Entity 변환
 */
@Repository
public class UserAddressRepositoryImpl implements UserAddressRepository {

    private final UserAddressJpaRepository userAddressJpaRepository;
    private final UserAddressMapper userAddressMapper;

    public UserAddressRepositoryImpl(UserAddressJpaRepository userAddressJpaRepository,
                                      UserAddressMapper userAddressMapper) {
        this.userAddressJpaRepository = userAddressJpaRepository;
        this.userAddressMapper = userAddressMapper;
    }

    @Override
    public UserAddress save(UserAddress userAddress) {
        // Domain → Entity
        UserAddressEntity entity = userAddressMapper.toEntity(userAddress);

        // JPA save
        UserAddressEntity saved = userAddressJpaRepository.save(entity);

        // Entity → Domain
        return userAddressMapper.toDomain(saved);
    }

    @Override
    public Optional<UserAddress> findById(Long id) {
        return userAddressJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(userAddressMapper::toDomain);  // Entity → Domain
    }

    @Override
    public long countByUserIdAndDeletedAtIsNull(Long userId) {
        return userAddressJpaRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public List<UserAddress> findAllByUserIdAndDeletedAtIsNull(Long userId) {
        return userAddressJpaRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(userAddressMapper::toDomain)  // Entity → Domain
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UserAddress> findFirstByUserIdAndDeletedAtIsNullAndIdNot(Long userId, Long excludeId) {
        return userAddressJpaRepository.findFirstByUserIdAndDeletedAtIsNullAndIdNotOrderByCreatedAtAsc(userId, excludeId)
                .map(userAddressMapper::toDomain);  // Entity → Domain
    }
}

