package com.loopers.infrastructure.address;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * UserAddressJpaRepository
 * Spring Data JPA Repository (UserAddressEntity 기준)
 */
public interface UserAddressJpaRepository extends JpaRepository<UserAddressEntity, Long> {

    Optional<UserAddressEntity> findByIdAndDeletedAtIsNull(Long id);

    long countByUserIdAndDeletedAtIsNull(Long userId);

    List<UserAddressEntity> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<UserAddressEntity> findFirstByUserIdAndDeletedAtIsNullAndIdNotOrderByCreatedAtAsc(Long userId, Long excludeId);
}

