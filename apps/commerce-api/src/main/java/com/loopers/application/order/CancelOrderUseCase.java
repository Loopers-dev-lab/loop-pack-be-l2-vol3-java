package com.loopers.application.order;

import com.loopers.domain.model.user.UserId;

public interface CancelOrderUseCase {

    void cancelOrder(UserId userId, Long orderId);
}
