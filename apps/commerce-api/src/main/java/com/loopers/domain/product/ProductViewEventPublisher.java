package com.loopers.domain.product;

public interface ProductViewEventPublisher {

    void publish(ProductViewEvent.Viewed event);
}
