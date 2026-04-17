package com.loopers.application.product;

import com.loopers.domain.event.ProductViewedEvent;
import com.loopers.domain.viewer.BotDetector;
import com.loopers.domain.viewer.ViewerIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ProductViewTracker {

    private final BotDetector botDetector;
    private final ViewerIdResolver viewerIdResolver;
    private final ApplicationEventPublisher eventPublisher;

    public void track(Long productId, Long userId, String anonymousId, String userAgent) {
        if (botDetector.isBot(userAgent)) {
            return;
        }

        String viewerId = viewerIdResolver.resolve(userId, anonymousId);
        eventPublisher.publishEvent(new ProductViewedEvent(viewerId, productId, Instant.now()));
    }

}
