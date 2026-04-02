package com.loopers.application.like;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.OutboxEventHelper;
import com.loopers.domain.like.LikeAction;
import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.UnlikedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeFacade {

    private final LikeService likeService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public LikeAction toggleLike(Long productId, Long userId) {
        if (likeService.isLiked(productId, userId)) {
            likeService.unlike(productId, userId);
            eventPublisher.publishEvent(new UnlikedEvent(productId));
            outboxEventRepository.save(OutboxEvent.create(
                "catalog-events",
                OutboxEventHelper.toJson(objectMapper, Map.of("type", "UNLIKED", "productId", productId, "occurredAt", ZonedDateTime.now().toString())),
                String.valueOf(productId)
            ));
            return LikeAction.UNLIKED;
        } else {
            likeService.like(productId, userId);
            eventPublisher.publishEvent(new LikedEvent(productId));
            outboxEventRepository.save(OutboxEvent.create(
                "catalog-events",
                OutboxEventHelper.toJson(objectMapper, Map.of("type", "LIKED", "productId", productId, "occurredAt", ZonedDateTime.now().toString())),
                String.valueOf(productId)
            ));
            return LikeAction.LIKED;
        }
    }

}
