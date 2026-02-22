package com.loopers.infrastructure.address;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserAddressRepositoryImpl implements UserAddressRepository {

    private final UserAddressJpaRepository userAddressJpaRepository;

    public UserAddressRepositoryImpl(UserAddressJpaRepository userAddressJpaRepository) {
        this.userAddressJpaRepository = userAddressJpaRepository;
    }

    @Override
    public UserAddress save(UserAddress userAddress) {
        return userAddressJpaRepository.save(userAddress);
    }

    @Override
    public Optional<UserAddress> findById(Long id) {
        return userAddressJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public long countByUserIdAndDeletedAtIsNull(Long userId) {
        return userAddressJpaRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public List<UserAddress> findAllByUserIdAndDeletedAtIsNull(Long userId) {
        return userAddressJpaRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
    }

    @Override
    public Optional<UserAddress> findFirstByUserIdAndDeletedAtIsNullAndIdNot(Long userId, Long excludeId) {
        return userAddressJpaRepository.findFirstByUserIdAndDeletedAtIsNullAndIdNotOrderByCreatedAtAsc(userId, excludeId);
    }
}
