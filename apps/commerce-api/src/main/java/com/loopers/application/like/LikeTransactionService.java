package com.loopers.application.like;

import com.loopers.domain.like.LikeResult;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeToggleService;
import com.loopers.domain.like.event.LikeToggledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeTransactionService {

    private final LikeService likeService;
    private final LikeToggleService likeToggleService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void doLike(Long userId, Long productId) {
        Optional<LikeModel> existing = likeService.findByUserIdAndProductId(userId, productId);

        LikeResult result = likeToggleService.like(existing, userId, productId);
        result.newLike().ifPresent(likeService::save);

        if (result.countChanged()) {
            eventPublisher.publishEvent(new LikeToggledEvent(productId, true));
        }
    }

    @Transactional
    public void doUnlike(Long userId, Long productId) {
        Optional<LikeModel> activeLike = likeService.findActiveLike(userId, productId);
        if (activeLike.isEmpty()) return;

        likeToggleService.unlike(activeLike.get());
        eventPublisher.publishEvent(new LikeToggledEvent(productId, false));
    }
}
