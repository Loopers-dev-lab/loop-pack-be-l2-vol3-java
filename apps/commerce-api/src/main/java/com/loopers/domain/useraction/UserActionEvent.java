package com.loopers.domain.useraction;

/**
 * 유저 행동 이벤트.
 * 상품 조회, 좋아요, 주문 등 유저의 주요 행동을 구조화된 이벤트로 표현한다.
 * Phase 2에서 Kafka product_metrics 토픽의 데이터 소스로 확장될 후보군.
 *
 * @param actionType  행동 유형 (PRODUCT_VIEW, LIKE_CREATE, LIKE_CANCEL, ORDER_CREATE)
 * @param userId      행동 주체 (비로그인 조회 시 null)
 * @param targetType  대상 유형 (PRODUCT, ORDER)
 * @param targetId    대상 ID
 * @param metadata    부가 정보 (JSON 형태의 문자열, 선택적)
 */
public record UserActionEvent(
        ActionType actionType,
        Long userId,
        String targetType,
        Long targetId,
        String metadata
) {
    public enum ActionType {
        PRODUCT_VIEW,
        LIKE_CREATE,
        LIKE_CANCEL,
        ORDER_CREATE
    }
}
