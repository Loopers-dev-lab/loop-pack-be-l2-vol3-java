package com.loopers.application.like;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.like.LikeActionResult;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.event.LikeRemovedEvent;
import com.loopers.domain.like.event.LikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LikeApp {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";

    private final LikeService likeService;
    private final LikeRepository likeRepository;
    private final OutboxAppender outboxAppender;
    private final ApplicationEventPublisher eventPublisher;

    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#productId"),
        @CacheEvict(value = "products", allEntries = true)
    })
    @Transactional
    public LikeInfo addLike(Long memberId, String productId) {
        LikeActionResult result = likeService.addLike(memberId, productId);
        if (result.added()) {
            LocalDateTime now = LocalDateTime.now();
            Long productDbId = result.likeModel().getRefProductId().value();
            eventPublisher.publishEvent(new LikedEvent(productDbId, memberId, now));
            outboxAppender.append("product", productId, "LikedEvent", CATALOG_EVENTS_TOPIC,
                    new LikeOutboxPayload(UUID.randomUUID().toString(), "LikedEvent", 1, productDbId, memberId, now, 1));
        }
        return LikeInfo.from(result.likeModel());
    }

    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#productId"),
        @CacheEvict(value = "products", allEntries = true)
    })
    @Transactional
    public void removeLike(Long memberId, String productId) {
        likeService.removeLike(memberId, productId).ifPresent(like -> {
            LocalDateTime now = LocalDateTime.now();
            Long productDbId = like.getRefProductId().value();
            eventPublisher.publishEvent(new LikeRemovedEvent(productDbId, memberId, now));
            outboxAppender.append("product", productId, "LikeRemovedEvent", CATALOG_EVENTS_TOPIC,
                    new LikeOutboxPayload(UUID.randomUUID().toString(), "LikeRemovedEvent", 1, productDbId, memberId, now, -1));
        });
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> getMyLikes(Long memberId, Pageable pageable) {
        return likeRepository.findByRefMemberId(new RefMemberId(memberId), pageable).map(LikeInfo::from);
    }
}
