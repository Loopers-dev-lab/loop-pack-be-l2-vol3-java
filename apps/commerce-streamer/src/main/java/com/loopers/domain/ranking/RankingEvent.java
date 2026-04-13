package com.loopers.domain.ranking;

import java.util.List;

/**
 * 랭킹 점수 계산 대상 이벤트.
 *
 * <p>sealed interface로 허용된 하위 타입을 봉인하여
 * switch 패턴 매칭 시 컴파일 타임에 누락을 방지한다.</p>
 */
public sealed interface RankingEvent {

    String eventId();

    /** 상품 조회 이벤트. */
    record View(String eventId, Long productId) implements RankingEvent {}

    /** 좋아요/취소 이벤트. */
    record Like(String eventId, Long productId, boolean liked) implements RankingEvent {}

    /** 주문 완료 이벤트. 이벤트 단위로 멱등성을 보장하며, 내부에서 항목별 점수를 계산한다. */
    record Order(String eventId, List<OrderItem> orderItems) implements RankingEvent {
        public record OrderItem(Long productId, Long price, Long quantity) {}
    }

    /** 상품 삭제 이벤트. 랭킹에서 해당 상품을 제거한다. */
    record Delete(String eventId, Long productId) implements RankingEvent {}
}
