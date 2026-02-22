package com.loopers.infrastructure.address;

import com.loopers.domain.address.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAddressJpaRepository extends JpaRepository<UserAddress, Long> {

    Optional<UserAddress> findByIdAndDeletedAtIsNull(Long id);

    long countByUserIdAndDeletedAtIsNull(Long userId);

    List<UserAddress> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<UserAddress> findFirstByUserIdAndDeletedAtIsNullAndIdNotOrderByCreatedAtAsc(Long userId, Long excludeId);
}
