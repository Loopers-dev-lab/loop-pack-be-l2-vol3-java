package com.loopers.application.order;

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
 * 어드민이 전체 주문 목록을 조회합니다.
 *
 * <p>모든 사용자의 주문을 최신순으로 페이징하여 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadOrdersUseCase {

    private final OrderRepository orderRepository;

    /**
     * @param pageSize 페이지 크기
     * @return 주문 목록 페이지 (최신순)
     */
    @Transactional(readOnly = true)
    public Page<OrderResult> execute(PageSize pageSize) {
        Slice<Order> orders = orderRepository.findAll(
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new Page<>(orders.getContent(), orders.hasNext())
                .map(OrderResult::from);
    }
}
