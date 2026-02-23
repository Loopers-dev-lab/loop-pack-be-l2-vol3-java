package com.loopers.interfaces.api.address;

import com.loopers.application.address.UserAddressFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class UserAddressController implements UserAddressApiSpec {

    private final UserAddressFacade userAddressFacade;

    public UserAddressController(UserAddressFacade userAddressFacade) {
        this.userAddressFacade = userAddressFacade;
    }

    @GetMapping
    @Override
    public ApiResponse<UserAddressResponse.AddressListResponse> getAddresses(@AuthUser User user) {
        UserAddressFacade.AddressListResult result = userAddressFacade.getAddresses(user.getId());

        List<UserAddressResponse.AddressSummary> summaries = result.addresses().stream()
                .map(addr -> new UserAddressResponse.AddressSummary(
                        addr.addressId(), addr.receiverName(), addr.phone(),
                        addr.zipCode(), addr.addressLine1(), addr.addressLine2(),
                        addr.isDefault()))
                .toList();

        return ApiResponse.success(new UserAddressResponse.AddressListResponse(summaries));
    }

    @PostMapping
    @Override
    public ApiResponse<Object> registerAddress(@AuthUser User user,
                                                @RequestBody UserAddressRequest.RegisterAddressRequest request) {
        userAddressFacade.register(user.getId(), request.receiverName(), request.phone(),
                request.zipCode(), request.addressLine1(), request.addressLine2());
        return ApiResponse.success();
    }

    @PutMapping("/{addressId}")
    @Override
    public ApiResponse<Object> updateAddress(@AuthUser User user,
                                              @PathVariable Long addressId,
                                              @RequestBody UserAddressRequest.UpdateAddressRequest request) {
        userAddressFacade.update(addressId, user.getId(), request.receiverName(), request.phone(),
                request.zipCode(), request.addressLine1(), request.addressLine2());
        return ApiResponse.success();
    }

    @DeleteMapping("/{addressId}")
    @Override
    public ApiResponse<Object> deleteAddress(@AuthUser User user,
                                              @PathVariable Long addressId) {
        userAddressFacade.delete(addressId, user.getId());
        return ApiResponse.success();
    }
}
