package com.loopers.infrastructure.order.repository.impl;

import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.repository.OrderProductRepository;
import com.loopers.infrastructure.order.entity.OrderProductEntity;
import com.loopers.infrastructure.order.repository.OrderProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderProductRepositoryImpl implements OrderProductRepository {

    private final OrderProductJpaRepository orderProductJpaRepository;

    @Override
    public List<OrderProduct> saveAll(Long orderId, List<OrderProduct> orderProducts) {
        List<OrderProductEntity> entities = orderProducts.stream()
                .map(op -> OrderProductEntity.toEntity(op, orderId))
                .toList();
        return orderProductJpaRepository.saveAll(entities).stream()
                .map(OrderProductEntity::toModel)
                .toList();
    }

    @Override
    public List<OrderProduct> findByOrderId(Long orderId) {
        return orderProductJpaRepository.findAllByOrderId(orderId).stream()
                .map(OrderProductEntity::toModel)
                .toList();
    }
}
