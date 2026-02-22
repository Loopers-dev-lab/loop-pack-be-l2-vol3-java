package com.loopers.interfaces.api.address;

import java.util.List;

public class UserAddressResponse {

    public record AddressSummary(
            Long addressId,
            String receiverName,
            String phone,
            String zipCode,
            String addressLine1,
            String addressLine2,
            boolean isDefault
    ) {}

    public record AddressListResponse(
            List<AddressSummary> addresses
    ) {}
}
