package com.loopers.domain.order;

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

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderModel placeOrder(Long userId, List<OrderItemModel> orderItems) {
        OrderModel order = new OrderModel(userId, orderItems);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getMyOrders(Long userId, LocalDate startAt, LocalDate endAt) {
        if (startAt.isAfter(endAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "startAt은 endAt보다 이후일 수 없습니다.");
        }

        ZonedDateTime startDateTime = startAt.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime endExclusive = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        return orderRepository.findAllByUserIdAndPeriod(userId, startDateTime, endExclusive);
    }

    @Transactional(readOnly = true)
    public OrderModel getMyOrder(Long userId, Long orderId) {
        return orderRepository.findDetailByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<OrderModel> getAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId) {
        return orderRepository.findDetailById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }
}
