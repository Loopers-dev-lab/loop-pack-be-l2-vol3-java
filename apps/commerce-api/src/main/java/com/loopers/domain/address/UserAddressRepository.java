package com.loopers.domain.address;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository {
    UserAddress save(UserAddress userAddress);
    Optional<UserAddress> findById(Long id);
    long countByUserIdAndDeletedAtIsNull(Long userId);
    List<UserAddress> findAllByUserIdAndDeletedAtIsNull(Long userId);
    Optional<UserAddress> findFirstByUserIdAndDeletedAtIsNullAndIdNot(Long userId, Long excludeId);
}
