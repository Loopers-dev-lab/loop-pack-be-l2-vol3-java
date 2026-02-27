package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 주문 도메인 Application Service.
 *
 * <p>단일 도메인 서비스(OrderService)만 호출하는 얇은 조회 메서드를 담당한다.
 * 고객용 메서드는 UserService 인증을 포함한다.
 * Model → Info 변환을 수행한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderAppService {

    private final OrderService orderService;
    private final UserService userService;

    /**
     * 주문 상세 정보를 조회한다.
     *
     * @param loginId 로그인 ID
     * @param loginPw 로그인 비밀번호
     * @param orderId 조회할 주문 ID
     * @return 주문 상세 정보
     */
    public OrderInfo getOrderDetail(String loginId, String loginPw, String orderId) {
        UserModel user = userService.authenticate(loginId, loginPw);
        OrderModel order = orderService.findByIdAndUserId(orderId, user.getUserId());
        List<OrderItemModel> items = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, items);
    }

    /**
     * 기간별 주문 목록을 조회한다.
     *
     * @param loginId 로그인 ID
     * @param loginPw 로그인 비밀번호
     * @param start   조회 시작 일시
     * @param end     조회 종료 일시
     * @return 조회 기간 내 주문 목록
     */
    public List<OrderInfo> getOrders(String loginId, String loginPw,
                                     LocalDateTime start, LocalDateTime end) {
        UserModel user = userService.authenticate(loginId, loginPw);
        List<OrderModel> orders = orderService.findAllByUserId(user.getUserId(), start, end);
        return toOrderInfoList(orders);
    }

    /**
     * 주문 ID로 주문 상세를 조회한다 (관리자용).
     *
     * @param orderId 주문 ID
     * @return 주문 정보 DTO
     */
    public OrderInfo findOrderById(String orderId) {
        OrderModel order = orderService.findOrderById(orderId);
        List<OrderItemModel> items = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, items);
    }

    /**
     * 기간별 전체 주문 목록을 조회한다 (관리자용).
     *
     * @param start 조회 시작 일시
     * @param end   조회 종료 일시
     * @return 주문 정보 DTO 목록
     */
    public List<OrderInfo> findAllOrders(LocalDateTime start, LocalDateTime end) {
        List<OrderModel> orders = orderService.findAllOrders(start, end);
        return toOrderInfoList(orders);
    }

    /**
     * 주문 목록을 배치 로딩으로 OrderInfo 목록으로 변환한다.
     * N+1 쿼리 대신 단일 IN 쿼리로 주문 항목을 일괄 조회한다.
     */
    private List<OrderInfo> toOrderInfoList(List<OrderModel> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<String> orderIds = orders.stream().map(OrderModel::getOrderId).toList();
        Map<String, List<OrderItemModel>> itemMap = orderService.findOrderItemsByOrderIds(orderIds);
        return orders.stream()
                .map(order -> OrderInfo.from(order, itemMap.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }
}
