package com.loopers.interfaces.api.order;

import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.order.query.OrderListByUserRequest;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.payment.CardType;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class OrderDto {

    public record CreateOrderRequest(
            @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다")
            @Valid
            List<OrderItemRequest> items,
            UUID couponId,
            @Min(value = 0, message = "사용 포인트는 0 이상이어야 합니다")
            int pointAmount,
            CardType cardType,
            @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호 형식이 올바르지 않습니다")
            String cardNo
    ) {
        private static final CardType DEFAULT_CARD_TYPE = CardType.SAMSUNG;
        private static final String DEFAULT_CARD_NO = "1234-5678-1234-5678";

        public CreateOrderRequest(List<OrderItemRequest> items) {
            this(items, null, 0, DEFAULT_CARD_TYPE, DEFAULT_CARD_NO);
        }

        public CreateOrderRequest(List<OrderItemRequest> items, UUID couponId) {
            this(items, couponId, 0, DEFAULT_CARD_TYPE, DEFAULT_CARD_NO);
        }

        public CreateOrderCommand toCommand(String memberId) {
            List<CreateOrderCommand.OrderItemCommand> itemCommands = items.stream()
                    .map(i -> new CreateOrderCommand.OrderItemCommand(i.productId(), i.quantity()))
                    .toList();
            CardType resolvedCardType = cardType == null ? DEFAULT_CARD_TYPE : cardType;
            String resolvedCardNo = cardNo == null || cardNo.isBlank() ? DEFAULT_CARD_NO : cardNo;
            return new CreateOrderCommand(memberId, itemCommands, couponId, pointAmount, resolvedCardType, resolvedCardNo);
        }
    }

    public record OrderItemRequest(
            @NotNull(message = "상품 ID는 필수입니다")
            UUID productId,
            @Min(value = 1, message = "수량은 1 이상이어야 합니다")
            int quantity
    ) {}

    public record OrderItemResponse(
            UUID id,
            UUID productId,
            int quantity,
            String snapshotProductName,
            int snapshotPrice,
            String snapshotBrandName
    ) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.id(),
                    item.productId(),
                    item.quantity(),
                    item.snapshotProductName(),
                    item.snapshotPrice(),
                    item.snapshotBrandName()
            );
        }
    }

    public record OrderResponse(
            UUID id,
            String memberId,
            String orderNumber,
            ZonedDateTime orderDate,
            String status,
            int totalAmount,
            UUID couponId,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.id(),
                    order.memberId(),
                    order.orderNumber(),
                    order.orderDate(),
                    order.status().name(),
                    order.totalAmount(),
                    order.couponId(),
                    order.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderListResponse(
            List<OrderResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static OrderListResponse from(Page<Order> pageData) {
            return new OrderListResponse(
                    pageData.getContent().stream().map(OrderResponse::from).toList(),
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }
    }

    public record ListOrdersRequest(
            @NotNull(message = "시작일은 필수입니다")
            @DateTimeFormat(pattern = "yyyyMMdd")
            LocalDate startAt,
            @NotNull(message = "종료일은 필수입니다")
            @DateTimeFormat(pattern = "yyyyMMdd")
            LocalDate endAt,
            Integer page,
            Integer size
    ) {
        private static final int DEFAULT_PAGE = 0;
        private static final int DEFAULT_SIZE = 20;

        public OrderListByUserRequest toQuery(String memberId) {
            int resolvedPage = page == null ? DEFAULT_PAGE : page;
            int resolvedSize = size == null ? DEFAULT_SIZE : size;
            Pageable pageable = PageRequest.of(resolvedPage, resolvedSize);
            return new OrderListByUserRequest(memberId, startAt, endAt, pageable);
        }
    }
}
