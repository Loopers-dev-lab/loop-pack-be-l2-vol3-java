package com.loopers.application.address;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 배송지 Facade
 *
 * UserAddressService를 위임하여 배송지 관련 유스케이스를 처리한다.
 */
@Component
public class UserAddressFacade {

    private final UserAddressService userAddressService;

    public UserAddressFacade(UserAddressService userAddressService) {
        this.userAddressService = userAddressService;
    }

    /** 배송지 목록 조회 */
    @Transactional(readOnly = true)
    public AddressListResult getAddresses(Long userId) {
        List<UserAddress> addresses = userAddressService.getAddresses(userId);

        List<AddressDetail> details = addresses.stream()
                .map(addr -> new AddressDetail(
                        addr.getId(), addr.getReceiverName(), addr.getPhone(),
                        addr.getZipCode(), addr.getAddressLine1(), addr.getAddressLine2(),
                        addr.isDefault()))
                .toList();

        return new AddressListResult(details);

    }

    /** 배송지 등록 */
    @Transactional
    public void register(Long userId, String receiverName, String phone,
                         String zipCode, String addressLine1, String addressLine2) {
        userAddressService.register(userId, receiverName, phone, zipCode, addressLine1, addressLine2);
    }

    /** 배송지 수정 */
    @Transactional
    public void update(Long addressId, Long userId, String receiverName, String phone,
                       String zipCode, String addressLine1, String addressLine2) {
        userAddressService.update(addressId, userId, receiverName, phone, zipCode, addressLine1, addressLine2);
    }

    /** 배송지 삭제 */
    @Transactional
    public void delete(Long addressId, Long userId) {
        userAddressService.delete(addressId, userId);
    }

    public record AddressDetail(
            Long addressId, String receiverName, String phone,
            String zipCode, String addressLine1, String addressLine2,
            boolean isDefault) {}

    public record AddressListResult(List<AddressDetail> addresses) {}
}
