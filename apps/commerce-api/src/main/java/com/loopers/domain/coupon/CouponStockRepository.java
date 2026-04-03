package com.loopers.domain.coupon;

/**
 * 쿠폰 재고 관리 Port.
 * Redis를 통한 선착순 쿠폰 재고 선검증 + 보정에 사용한다.
 */
public interface CouponStockRepository {

    /**
     * 선착순 쿠폰 발급 요청 — 중복 체크 + 조건부 재고 차감을 원자적으로 처리.
     * Lua 스크립트를 통해 하나의 Redis 호출로 실행된다.
     *
     * @return 0 이상 = 성공(잔여 수량), -1 = 매진, -2 = 중복 요청
     */
    long tryIssueRequest(Long couponTemplateId, Long userId);

    /**
     * 재고 설정. 보정 스케줄러에서 DB 기준으로 동기화 시 사용.
     */
    void setStock(Long couponTemplateId, int stock);
}
