package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductCacheEvictEvent;
import com.loopers.domain.product.ProductDetailCacheEvictEvent;
import com.loopers.domain.product.ProductEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductSpringEventPublisher implements ProductEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(ProductCacheEvictEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(ProductDetailCacheEvictEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
