package com.loopers.infrastructure.order;

import com.loopers.domain.common.vo.Address;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OrderMapper
 * Domain POJO ↔ JPA Entity 변환
 * 1:N 관계 (Order ↔ OrderItem) 처리
 */
@Component
public class OrderMapper {

    /**
     * Domain → JPA Entity
     */
    public OrderEntity toEntity(Order domain) {
        OrderEntity entity = new OrderEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setOrderNumber(domain.getOrderNumber());

        // OrderItem List 변환
        List<OrderItemEntity> itemEntities = domain.getItems().stream()
                .map(this::toOrderItemEntity)
                .collect(Collectors.toList());
        entity.setItems(itemEntities);

        // Money VO → int 변환
        entity.setSubtotalAmount(domain.getSubtotalAmount());
        entity.setDiscountAmount(domain.getDiscountAmount());
        entity.setPointUsedAmount(domain.getPointUsedAmount());
        entity.setShippingFee(domain.getShippingFee());
        entity.setTotalAmount(domain.getTotalAmount());

        entity.setStatus(domain.getStatus());
        entity.setOrdererName(domain.getOrdererName());
        entity.setOrdererPhone(domain.getOrdererPhone());
        entity.setReceiverName(domain.getReceiverName());
        entity.setReceiverPhone(domain.getReceiverPhone());

        // Address VO → 개별 필드 분해
        entity.setZipCode(domain.getZipCode());
        entity.setAddressLine1(domain.getAddressLine1());
        entity.setAddressLine2(domain.getAddressLine2());

        entity.setCouponId(domain.getCouponId());
        entity.setPaymentId(domain.getPaymentId());
        entity.setPaymentMethod(domain.getPaymentMethod());
        entity.setOrderedAt(domain.getOrderedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setCanceledAt(domain.getCanceledAt());
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(domain.getDeletedAt());

        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public Order toDomain(OrderEntity entity) {
        // OrderItem List 변환
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toOrderItemDomain)
                .collect(Collectors.toList());

        // 개별 필드 → Address VO 재조합
        Address address = new Address(
                entity.getZipCode(),
                entity.getAddressLine1(),
                entity.getAddressLine2()
        );

        return Order.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getOrderNumber(),
                items,
                entity.getSubtotalAmount(),
                entity.getDiscountAmount(),
                entity.getPointUsedAmount(),
                entity.getShippingFee(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.getOrdererName(),
                entity.getOrdererPhone(),
                entity.getReceiverName(),
                entity.getReceiverPhone(),
                address,
                entity.getCouponId(),
                entity.getPaymentId(),
                entity.getPaymentMethod(),
                entity.getOrderedAt(),
                entity.getExpiresAt(),
                entity.getCanceledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }

    /**
     * JPA Entity → Domain (items 제외 — 목록 조회 전용)
     */
    public Order toDomainWithoutItems(OrderEntity entity) {
        Address address = new Address(
                entity.getZipCode(),
                entity.getAddressLine1(),
                entity.getAddressLine2()
        );

        return Order.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getOrderNumber(),
                List.of(),
                entity.getSubtotalAmount(),
                entity.getDiscountAmount(),
                entity.getPointUsedAmount(),
                entity.getShippingFee(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.getOrdererName(),
                entity.getOrdererPhone(),
                entity.getReceiverName(),
                entity.getReceiverPhone(),
                address,
                entity.getCouponId(),
                entity.getPaymentId(),
                entity.getPaymentMethod(),
                entity.getOrderedAt(),
                entity.getExpiresAt(),
                entity.getCanceledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }

    /**
     * OrderItem Domain → Entity
     */
    private OrderItemEntity toOrderItemEntity(OrderItem domain) {
        OrderItemEntity entity = new OrderItemEntity();
        entity.setId(domain.getId());
        entity.setProductId(domain.getProductId());
        entity.setProductName(domain.getProductName());
        entity.setBrandName(domain.getBrandName());
        entity.setUnitPrice(domain.getUnitPrice());
        entity.setQuantity(domain.getQuantity());
        return entity;
    }

    /**
     * OrderItem Entity → Domain
     */
    private OrderItem toOrderItemDomain(OrderItemEntity entity) {
        return OrderItem.reconstitute(
                entity.getId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getBrandName(),
                entity.getUnitPrice(),
                entity.getQuantity()
        );
    }
}
