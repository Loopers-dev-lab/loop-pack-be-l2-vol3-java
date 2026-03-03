package com.loopers.application.order;

import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryApplicationService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public boolean existsOrderItemByProductId(UUID productId) {
        return orderRepository.existsOrderItemByProductId(productId);
    }
}
