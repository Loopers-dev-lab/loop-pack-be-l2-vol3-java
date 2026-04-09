package com.loopers.domain.event;

import java.util.List;

/**
 * 주문 결제 성공 시 발행되는 도메인 이벤트.
 *
 * 발행 시점:
 * - 전액 할인으로 결제 없이 주문 완료된 경우
 * - PG 결제가 SUCCESS로 확정된 경우
 *
 * 구독자:
 * - {@code OrderEventListener} — 주문 결제 완료 로깅
 * - {@code UserActionEventListener} — 유저 행동(ORDER_PAID) 로깅
 *
 * @param orderId          결제가 완료된 주문 ID
 * @param memberId         주문한 회원 ID
 * @param totalAmount      할인 전 주문 총 금액 (집계/통계용)
 * @param orderedProducts  주문 상품 목록 — Consumer가 상품별 주문 수량/금액을 집계하여
 *                          랭킹 점수(R9) 의 order 가중치 입력으로 사용한다.
 */
public record OrderPaidEvent(Long orderId, Long memberId, int totalAmount,
                              List<OrderedProduct> orderedProducts) {

    /**
     * 주문에 포함된 개별 상품 정보.
     *
     * @param productId 상품 ID
     * @param quantity  주문 수량
     * @param unitPrice 주문 시점 단가 — R9 랭킹 점수 산정에서 line amount(unitPrice * quantity) 입력
     */
    public record OrderedProduct(Long productId, int quantity, int unitPrice) {}
}
