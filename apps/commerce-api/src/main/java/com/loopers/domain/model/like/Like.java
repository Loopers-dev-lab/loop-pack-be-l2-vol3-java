package com.loopers.domain.model.like;

import com.loopers.domain.model.common.AggregateRoot;
import com.loopers.domain.model.like.event.ProductLikedEvent;
import com.loopers.domain.model.like.event.ProductUnlikedEvent;
import com.loopers.domain.model.user.UserId;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Like extends AggregateRoot {

    private final Long id;
    private final UserId userId;
    private final Long productId;
    private final LocalDateTime createdAt;

    private Like(Long id, UserId userId, Long productId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.createdAt = createdAt;
    }

    public static Like create(UserId userId, Long productId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        Like like = new Like(null, userId, productId, LocalDateTime.now());
        like.registerEvent(new ProductLikedEvent(productId));
        return like;
    }

    public Like markUnliked() {
        Like unliked = new Like(this.id, this.userId, this.productId, this.createdAt);
        unliked.registerEvent(new ProductUnlikedEvent(this.productId));
        return unliked;
    }

    public static Like reconstitute(Long id, UserId userId, Long productId, LocalDateTime createdAt) {
        return new Like(id, userId, productId, createdAt);
    }
}
