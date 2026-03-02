package com.loopers.interfaces.api.address;

public class UserAddressRequest {

    public record RegisterAddressRequest(
            String receiverName,
            String phone,
            String zipCode,
            String addressLine1,
            String addressLine2
    ) {}

    public record UpdateAddressRequest(
            String receiverName,
            String phone,
            String zipCode,
            String addressLine1,
            String addressLine2
    ) {}
}
