package com.loopers.domain.address;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserAddressErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;

    public UserAddressService(UserAddressRepository userAddressRepository) {
        this.userAddressRepository = userAddressRepository;
    }

    @Transactional
    public UserAddress register(Long userId, String receiverName, String phone,
                                 String zipCode, String addressLine1, String addressLine2) {
        UserAddress address = UserAddress.create(userId, receiverName, phone, zipCode, addressLine1, addressLine2);

        long existingCount = userAddressRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (existingCount == 0) {
            address.setAsDefault();
        }

        return userAddressRepository.save(address);
    }

    @Transactional
    public void update(Long addressId, Long userId, String receiverName, String phone,
                       String zipCode, String addressLine1, String addressLine2) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new CoreException(UserAddressErrorType.ADDRESS_NOT_FOUND));
        address.validateOwnership(userId);
        address.update(receiverName, phone, zipCode, addressLine1, addressLine2);
        userAddressRepository.save(address);
    }

    @Transactional
    public void delete(Long addressId, Long userId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new CoreException(UserAddressErrorType.ADDRESS_NOT_FOUND));
        address.validateOwnership(userId);
        boolean wasDefault = address.isDefault();
        address.delete();
        if (wasDefault) {
            address.unsetDefault();
        }
        userAddressRepository.save(address);

        if (wasDefault) {
            userAddressRepository.findFirstByUserIdAndDeletedAtIsNullAndIdNot(userId, addressId)
                    .ifPresent(nextDefault -> {
                        nextDefault.setAsDefault();
                        userAddressRepository.save(nextDefault);
                    });
        }
    }

    @Transactional(readOnly = true)
    public List<UserAddress> getAddresses(Long userId) {
        return userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public UserAddress getAddress(Long addressId, Long userId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new CoreException(UserAddressErrorType.ADDRESS_NOT_FOUND));
        address.validateOwnership(userId);
        return address;
    }
}
