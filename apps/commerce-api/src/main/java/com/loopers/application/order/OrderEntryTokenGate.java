package com.loopers.application.order;

import com.loopers.application.queue.QueueOrderProperties;
import com.loopers.domain.queue.OrderEntryTokenService;
import org.springframework.stereotype.Component;

/**
 * 대기열 입장 토큰을 주문 플로우에 묶는 애플리케이션 계층 관문.
 * <p>
 * {@link OrderFacade#placeOrder} 초입에서 호출되며, {@link QueueOrderProperties#requireEntryToken()}이 {@code true}일 때만
 * {@link OrderEntryTokenService}로 검증·일회 소비를 수행한다. {@code false}이면 호출을 통과시키며,
 * 대기열 없이 주문 API를 쓰는 환경(local·비대기열 운영)에 맞춘다.
 * <p>
 * 설정 키: {@code queue.order.require-entry-token}. 클라이언트는 활성화 시 {@code X-Entry-Token} 헤더로
 * 스케줄러가 발급한 토큰을 제출해야 한다.
 */
@Component
public class OrderEntryTokenGate {

    private final QueueOrderProperties queueOrderProperties;
    private final OrderEntryTokenService orderEntryTokenService;

    public OrderEntryTokenGate(
            QueueOrderProperties queueOrderProperties,
            OrderEntryTokenService orderEntryTokenService
    ) {
        this.queueOrderProperties = queueOrderProperties;
        this.orderEntryTokenService = orderEntryTokenService;
    }

    /**
     * 입장 토큰 검증이 켜져 있으면 {@code userId}와 일치하는 Redis 입장 자격을 확인하고 소비한다.
     * 꺼져 있으면 즉시 반환한다.
     *
     * @param userId       주문 주체(로그인 유저 id)
     * @param xEntryToken {@code X-Entry-Token} 원문. 검증 활성 시 누락·불일치면 {@code CoreException}(BAD_REQUEST)
     */
    public void verifyAndConsumeIfRequired(Long userId, String xEntryToken) {
        if (!queueOrderProperties.requireEntryToken()) {
            return;
        }
        orderEntryTokenService.assertValidAndConsume(userId, xEntryToken);
    }
}
