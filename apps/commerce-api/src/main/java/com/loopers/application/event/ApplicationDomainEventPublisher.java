package com.loopers.application.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.UUID;

@Component
public class ApplicationDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public ApplicationDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishOrderPlaced(Long orderId, Long userId, java.util.List<AppEvents.OrderItemPayload> items, Long totalAmount) {
        applicationEventPublisher.publishEvent(new AppEvents.OrderPlacedApplicationEvent(
            UUID.randomUUID().toString(),
            orderId,
            userId,
            ZonedDateTime.now(),
            items,
            totalAmount
        ));
    }

    public void publishProductLikeChanged(Long productId, Long userId, int delta) {
        applicationEventPublisher.publishEvent(new AppEvents.ProductLikeChangedApplicationEvent(
            UUID.randomUUID().toString(),
            productId,
            userId,
            delta,
            ZonedDateTime.now()
        ));
    }

    public void publishProductViewed(Long productId, Long userId) {
        applicationEventPublisher.publishEvent(new AppEvents.ProductViewedApplicationEvent(
            UUID.randomUUID().toString(),
            productId,
            userId,
            ZonedDateTime.now()
        ));
    }

    public void publishProductClicked(Long productId, Long userId) {
        applicationEventPublisher.publishEvent(new AppEvents.ProductClickedApplicationEvent(
            UUID.randomUUID().toString(),
            productId,
            userId,
            ZonedDateTime.now()
        ));
    }

    public void publishProductDwelled(Long productId, Long userId, int dwellTimeSeconds) {
        applicationEventPublisher.publishEvent(new AppEvents.ProductDwelledApplicationEvent(
            UUID.randomUUID().toString(),
            productId,
            userId,
            dwellTimeSeconds,
            ZonedDateTime.now()
        ));
    }

    public void publishCouponIssueRequested(Long requestId, Long couponId, Long userId) {
        applicationEventPublisher.publishEvent(new AppEvents.CouponIssueRequestedApplicationEvent(
            UUID.randomUUID().toString(),
            requestId,
            couponId,
            userId,
            ZonedDateTime.now()
        ));
    }
}
