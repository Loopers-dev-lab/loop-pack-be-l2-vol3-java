package com.loopers.domain.like;

import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.domain.AbstractAggregateRoot;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_likes_user_id_product_id", columnNames = {"user_id", "product_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Like extends AbstractAggregateRoot<Like> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private ZonedDateTime likedAt;

    public static Like create(Long userId, Long productId) {
        if (Objects.isNull(userId)) {
            throw new CoreException(ErrorType.REQUIRED_USER_ID);
        }
        if (Objects.isNull(productId)) {
            throw new CoreException(ErrorType.REQUIRED_PRODUCT_ID);
        }

        Like like = new Like();
        like.userId = userId;
        like.productId = productId;
        like.likedAt = ZonedDateTime.now();
        like.registerEvent(LikeEvent.Liked.from(like));
        return like;
    }

    /**
     * 좋아요 취소 이벤트를 등록한다.
     *
     * <p>{@code repository.delete()} 시점에 {@link LikeEvent.Unliked} 이벤트가 발행된다.</p>
     */
    public void unlike() {
        registerEvent(LikeEvent.Unliked.from(this));
    }
}
