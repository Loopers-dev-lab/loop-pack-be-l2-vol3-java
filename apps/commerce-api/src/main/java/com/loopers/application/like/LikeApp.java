package com.loopers.application.like;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.like.LikeActionResult;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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

    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#productId"),
        @CacheEvict(value = "products", allEntries = true)
    })
    @Transactional
    public LikeInfo addLike(Long memberId, String productId) {
        LikeActionResult result = likeService.addLike(memberId, productId);
        if (result.added()) {
            Long productDbId = result.likeModel().getRefProductId().value();
            LikeOutboxPayload payload = new LikeOutboxPayload(
                    UUID.randomUUID().toString(), "LikedEvent", 1,
                    productDbId, memberId, 1, LocalDateTime.now());
            outboxAppender.append("like", productId, "LikedEvent", CATALOG_EVENTS_TOPIC, payload);
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
            Long productDbId = like.getRefProductId().value();
            LikeOutboxPayload payload = new LikeOutboxPayload(
                    UUID.randomUUID().toString(), "LikeRemovedEvent", 1,
                    productDbId, memberId, -1, LocalDateTime.now());
            outboxAppender.append("like", productId, "LikeRemovedEvent", CATALOG_EVENTS_TOPIC, payload);
        });
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> getMyLikes(Long memberId, Pageable pageable) {
        return likeRepository.findByRefMemberId(new RefMemberId(memberId), pageable).map(LikeInfo::from);
    }
}
