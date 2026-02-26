package com.loopers.application.order;

import com.loopers.domain.model.user.UserId;

public interface UpdateDeliveryAddressUseCase {

    void updateDeliveryAddress(UserId userId, Long orderId, String newAddress);
}
