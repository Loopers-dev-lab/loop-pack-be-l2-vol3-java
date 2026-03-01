package com.loopers.application.order;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 자신의 주문 목록을 조회합니다.
 *
 * <p>시작일과 종료일 범위 내의 주문을 최신순으로 페이징하여 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadMyOrdersUseCase {

    private final OrderRepository orderRepository;

    /**
     * @param userId 사용자 ID
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     * @param pageSize 페이지 크기
     * @return 주문 목록 페이지 (최신순)
     */
    @Transactional(readOnly = true)
    public Page<OrderResult> execute(Long userId, LocalDate startDate, LocalDate endDate, PageSize pageSize) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        Slice<Order> orders = orderRepository.findAllByUserIdAndOrderedAtBetween(
                userId,
                startDateTime,
                endDateTime,
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "orderedAt"))
        );
        return new Page<>(orders.getContent(), orders.hasNext())
                .map(OrderResult::from);
    }
}
