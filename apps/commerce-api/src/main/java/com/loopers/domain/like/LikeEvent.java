package com.loopers.domain.like;

/**
 * 좋아요 도메인에서 발생하는 이벤트.
 *
 * <p>Like 엔티티에서 {@code registerEvent()}로 등록되며,
 * {@code repository.save()} 또는 {@code repository.delete()} 시점에 발행된다.</p>
 */
public class LikeEvent {

    /**
     * 좋아요가 생성되었을 때 발행되는 이벤트.
     *
     * @param productId 좋아요된 상품 ID
     */
    public record Liked(Long productId) {
    }

    /**
     * 좋아요가 취소되었을 때 발행되는 이벤트.
     *
     * @param productId 좋아요가 취소된 상품 ID
     */
    public record Unliked(Long productId) {
    }
}
