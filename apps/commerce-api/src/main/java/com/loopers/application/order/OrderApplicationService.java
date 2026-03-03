package com.loopers.application.order;

import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.order.query.OrderListByUserRequest;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderItem;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order create(UUID userId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        String orderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        Order order = new Order(userId, orderNumber, items);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancel(OrderAccessRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!request.isAdmin() && !order.isOwner(request.userId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "타인의 주문을 취소할 수 없습니다.");
        }

        Order cancelled = order.cancel(); // 이미 취소된 경우 409 CONFLICT 던짐
        return orderRepository.save(cancelled);
    }

    @Transactional(readOnly = true)
    public Order getById(OrderAccessRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!request.isAdmin() && !order.isOwner(request.userId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "타인의 주문을 조회할 수 없습니다.");
        }

        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> listByUser(OrderListByUserRequest request) {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime startDateTime = request.startAt().atStartOfDay(kst);
        ZonedDateTime endDateTime = request.endAt().atTime(23, 59, 59).atZone(kst);
        return orderRepository.findByUserId(request.userId(), startDateTime, endDateTime, request.pageable());
    }

    @Transactional(readOnly = true)
    public Page<Order> listAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
