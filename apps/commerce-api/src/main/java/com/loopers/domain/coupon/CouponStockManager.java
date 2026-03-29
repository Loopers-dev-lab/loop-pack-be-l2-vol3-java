package com.loopers.domain.coupon;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * 쿠폰 재고 관리를 위한 도메인 포트.
 *
 * <p>수량 검증과 중복 발급 검증을 원자적으로 수행한다.
 * 인프라스트럭처 계층에서 구체적인 저장소(Redis 등)로 구현한다.</p>
 */
public interface CouponStockManager {

    /**
     * 쿠폰의 잔여 발급 수량을 등록한다.
     *
     * <p>쿠폰 생성 시 호출되어 초기 재고를 세팅한다.
     * 이미 등록된 경우 덮어쓰지 않는다.
     * 쿠폰 만료일까지의 남은 시간을 TTL로 설정한다.</p>
     *
     * @param couponId      쿠폰 ID
     * @param totalQuantity 총 발급 가능 수량
     * @param expiredAt     쿠폰 만료일 (TTL 기준)
     */
    void initialize(Long couponId, int totalQuantity, ZonedDateTime expiredAt);

    /**
     * 쿠폰 발급을 시도한다.
     *
     * <p>수량 초과 여부와 중복 발급 여부를 원자적으로 검증하고,
     * 성공 시 잔여 수량을 차감하고 사용자를 발급 목록에 추가한다.</p>
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 발급 시도 결과
     */
    CouponIssueStatus issueCoupon(Long couponId, Long userId);

    /**
     * DB 기준으로 재고 상태를 동기화한다.
     *
     * <p>스케줄러에서 호출하여 Redis 상태를 DB 기준으로 덮어쓴다.
     * 쿠폰 만료일까지의 남은 시간을 TTL로 재설정한다.</p>
     *
     * @param couponId  쿠폰 ID
     * @param stock     잔여 수량 (totalQuantity - 실제 발급 수)
     * @param userIds   발급된 사용자 ID 집합
     * @param expiredAt 쿠폰 만료일 (TTL 기준)
     */
    void sync(Long couponId, int stock, Set<Long> userIds, ZonedDateTime expiredAt);

    /**
     * 쿠폰 발급을 롤백한다.
     *
     * <p>후속 처리가 실패했을 때 호출하여
     * 재고를 원복하고 발급 기록을 제거한다.</p>
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     */
    void rollback(Long couponId, Long userId);
}
