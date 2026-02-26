package com.loopers.domain.model.order;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class DeliveryInfo {

    private final String receiverName;
    private final String address;
    private final String deliveryRequest;
    private final LocalDate desiredDeliveryDate;

    private DeliveryInfo(String receiverName, String address,
                         String deliveryRequest, LocalDate desiredDeliveryDate) {
        if (receiverName == null || receiverName.isBlank()) {
            throw new IllegalArgumentException("수령인 이름은 필수입니다.");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("배송 주소는 필수입니다.");
        }
        this.receiverName = receiverName.trim();
        this.address = address.trim();
        this.deliveryRequest = deliveryRequest;
        this.desiredDeliveryDate = desiredDeliveryDate;
    }

    public static DeliveryInfo of(String receiverName, String address,
                                  String deliveryRequest, LocalDate desiredDeliveryDate) {
        return new DeliveryInfo(receiverName, address, deliveryRequest, desiredDeliveryDate);
    }

    public DeliveryInfo withAddress(String newAddress) {
        return new DeliveryInfo(this.receiverName, newAddress, this.deliveryRequest, this.desiredDeliveryDate);
    }
}
