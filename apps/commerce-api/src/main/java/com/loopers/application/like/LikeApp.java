package com.loopers.application.like;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.like.LikeActionResult;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.event.LikedEvent;
import com.loopers.domain.like.event.LikeRemovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
public class LikeApp {

    private final LikeService likeService;
    private final LikeRepository likeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#productId"),
        @CacheEvict(value = "products", allEntries = true)
    })
    @Transactional
    public LikeInfo addLike(Long memberId, String productId) {
        LikeActionResult result = likeService.addLike(memberId, productId);
        if (result.added()) {
            eventPublisher.publishEvent(new LikedEvent(
                    result.likeModel().getRefProductId().value(), memberId, LocalDateTime.now()));
        }
        return LikeInfo.from(result.likeModel());
    }

    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#productId"),
        @CacheEvict(value = "products", allEntries = true)
    })
    @Transactional
    public void removeLike(Long memberId, String productId) {
        likeService.removeLike(memberId, productId).ifPresent(like ->
                eventPublisher.publishEvent(new LikeRemovedEvent(
                        like.getRefProductId().value(), memberId, LocalDateTime.now())));
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> getMyLikes(Long memberId, Pageable pageable) {
        return likeRepository.findByRefMemberId(new RefMemberId(memberId), pageable).map(LikeInfo::from);
    }
}
