package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderItemModel save(OrderItemModel orderItemModel) {
        return orderItemJpaRepository.save(orderItemModel);
    }

    @Override
    public List<OrderItemModel> findAllByOrderId(Long orderId) {
        return orderItemJpaRepository.findAllByOrderId(orderId);
    }
}
