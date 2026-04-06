package com.loopers.domain.ranking;

/**
 * 랭킹 점수 계산 대상 이벤트.
 *
 * <p>sealed interface로 허용된 하위 타입을 봉인하여
 * switch 패턴 매칭 시 컴파일 타임에 누락을 방지한다.</p>
 */
public sealed interface RankingEvent {

    String eventId();

    Long productId();

    /** 상품 조회 이벤트. */
    record View(String eventId, Long productId) implements RankingEvent {}

    /** 좋아요/취소 이벤트. */
    record Like(String eventId, Long productId, boolean liked) implements RankingEvent {}

    /** 주문 완료 이벤트. 주문 항목별로 생성된다. */
    record Order(String eventId, Long productId, Long price, Long quantity) implements RankingEvent {}
}
