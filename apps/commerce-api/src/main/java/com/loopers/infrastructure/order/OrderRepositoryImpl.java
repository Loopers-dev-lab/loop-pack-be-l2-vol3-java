package com.loopers.infrastructure.order;

import com.loopers.domain.model.order.*;
import com.loopers.domain.model.user.UserId;
import com.loopers.domain.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = toEntity(order);
        OrderJpaEntity saved = orderJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public List<Order> findAllByUserId(UserId userId) {
        return orderJpaRepository.findAllByUserId(userId.getValue()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Order> findAllByUserIdAndDateRange(UserId userId, LocalDateTime startAt, LocalDateTime endAt) {
        return orderJpaRepository.findAllByUserIdAndCreatedAtBetween(userId.getValue(), startAt, endAt).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private OrderJpaEntity toEntity(Order order) {
        List<OrderItemJpaEntity> itemEntities = order.getItems().stream()
                .map(this::toItemEntity)
                .toList();

        OrderSnapshotJpaEntity snapshotEntity = null;
        if (order.getSnapshot() != null) {
            snapshotEntity = toSnapshotEntity(order.getSnapshot());
        }

        return new OrderJpaEntity(
                order.getId(),
                order.getUserId().getValue(),
                itemEntities,
                snapshotEntity,
                order.getReceiverName(),
                order.getAddress(),
                order.getDeliveryRequest(),
                order.getPaymentMethod().name(),
                order.getTotalAmount().getValue(),
                order.getDiscountAmount().getValue(),
                order.getPaymentAmount().getValue(),
                order.getStatus().name(),
                order.getDesiredDeliveryDate(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemJpaEntity toItemEntity(OrderItem item) {
        return new OrderItemJpaEntity(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice().getValue()
        );
    }

    private OrderSnapshotJpaEntity toSnapshotEntity(OrderSnapshot snapshot) {
        return new OrderSnapshotJpaEntity(
                snapshot.getId(),
                snapshot.getSnapshotData(),
                snapshot.getCreatedAt()
        );
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toItemDomain)
                .toList();

        OrderSnapshot snapshot = null;
        if (entity.getSnapshot() != null) {
            snapshot = OrderSnapshot.reconstitute(
                    entity.getSnapshot().getId(),
                    entity.getSnapshot().getSnapshotData(),
                    entity.getSnapshot().getCreatedAt()
            );
        }

        DeliveryInfo deliveryInfo = DeliveryInfo.of(
                entity.getReceiverName(),
                entity.getAddress(),
                entity.getDeliveryRequest(),
                entity.getDesiredDeliveryDate()
        );

        OrderAmount orderAmount = OrderAmount.reconstitute(
                PaymentMethod.valueOf(entity.getPaymentMethod()),
                Money.of(entity.getTotalAmount()),
                Money.of(entity.getDiscountAmount()),
                Money.of(entity.getPaymentAmount())
        );

        return Order.reconstitute(new OrderData(
                entity.getId(),
                UserId.of(entity.getUserId()),
                items,
                snapshot,
                deliveryInfo,
                orderAmount,
                OrderStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        ));
    }

    private OrderItem toItemDomain(OrderItemJpaEntity entity) {
        return OrderItem.reconstitute(
                entity.getId(),
                entity.getProductId(),
                entity.getQuantity(),
                Money.of(entity.getUnitPrice())
        );
    }
}
