package com.loopers.domain.address;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserAddressErrorType;

public class UserAddressService {

    private final UserAddressRepository userAddressRepository;

    public UserAddressService(UserAddressRepository userAddressRepository) {
        this.userAddressRepository = userAddressRepository;
    }

    public UserAddress register(Long userId, String receiverName, String phone,
                                 String zipCode, String addressLine1, String addressLine2) {
        UserAddress address = UserAddress.create(userId, receiverName, phone, zipCode, addressLine1, addressLine2);

        long existingCount = userAddressRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (existingCount == 0) {
            address.setAsDefault();
        }

        return userAddressRepository.save(address);
    }

    public void update(Long addressId, Long userId, String receiverName, String phone,
                       String zipCode, String addressLine1, String addressLine2) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new CoreException(UserAddressErrorType.ADDRESS_NOT_FOUND));
        address.validateOwnership(userId);
        address.update(receiverName, phone, zipCode, addressLine1, addressLine2);
    }

    public void delete(Long addressId, Long userId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new CoreException(UserAddressErrorType.ADDRESS_NOT_FOUND));
        address.validateOwnership(userId);
        address.delete();

        if (address.isDefault()) {
            address.unsetDefault();
            userAddressRepository.findFirstByUserIdAndDeletedAtIsNullAndIdNot(userId, addressId)
                    .ifPresent(UserAddress::setAsDefault);
        }
    }
}
