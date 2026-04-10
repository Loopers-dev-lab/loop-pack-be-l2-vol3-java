package com.loopers.domain.order;

import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 주문 도메인 서비스.
 * <p>
 * 주문의 순수 도메인 로직(상태 전이, 저장, 조회)을 담당한다.
 * 상품/브랜드/재고/장바구니 등 외부 도메인 조합(orchestration)은
 * {@link com.loopers.application.order.OrderFacade}에서 수행한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCartRestoreRepository orderCartRestoreRepository;

    /**
     * 주문 항목 유효성 검증 및 병합/정렬을 수행한다.
     * <p>
     * 주문 항목이 비어있지 않은지, 결제 대기 주문 수 제한을 초과하지 않는지 검증한 뒤,
     * 동일 상품 병합 및 productId 오름차순 정렬을 수행한다.
     * </p>
     *
     * @param userId 주문자 ID
     * @param items  주문 항목 요청 목록
     * @return 병합/정렬된 주문 항목 목록
     * @throws CoreException 주문 항목이 비어있거나, 결제 대기 주문 수 초과 시
     */
    public List<OrderItemCommand> validateAndPrepare(Long userId, List<OrderItemCommand> items) {
        validateNotEmpty(items);
        return mergeAndSort(items);
    }

    /**
     * 주문과 주문 항목을 저장한다.
     *
     * @param userId      주문자 ID
     * @param orderType   주문 유형 (DIRECT / CART)
     * @param totalAmount 총 결제 금액
     * @param snapshots   주문 항목 스냅샷 목록
     * @return 저장된 주문 엔티티
     */
    @Transactional
    public OrderModel createOrder(Long userId, OrderType orderType,
                                  BigDecimal totalAmount, List<OrderItemSnapshot> snapshots) {
        OrderModel order = OrderModel.create(userId, orderType, totalAmount);
        order = orderRepository.save(order);
        saveOrderItems(order, userId, snapshots);
        return order;
    }

    /**
     * 사용자가 주문을 취소한다.
     * <p>
     * CAS(Compare-And-Set) 방식으로 PENDING_PAYMENT -> CANCELLED 상태 전이를 수행한다.
     * 이미 CANCELLED 상태면 멱등 처리(빈 Optional 반환). 상태 전이 후 주문 엔티티를 반환한다.
     * 재고 해제 및 장바구니 복원은 Facade에서 수행한다.
     * </p>
     *
     * @param userId  사용자 ID
     * @param orderId 주문 ID
     * @return 취소된 주문 엔티티 (이미 취소된 경우 빈 Optional)
     * @throws CoreException 주문이 존재하지 않거나 취소 불가한 상태일 때
     */
    @Transactional
    public Optional<OrderModel> cancelOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));

        int affected = orderRepository.casUpdateStatus(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
        if (affected == 0) {
            OrderModel current = orderRepository.findById(orderId)
                    .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
            if (current.getStatus() == OrderStatus.CANCELLED) {
                return Optional.empty();
            }
            throw new CoreException(ErrorType.ORDER_NOT_CANCELLABLE);
        }

        return Optional.of(order);
    }

    /**
     * 배치 스케줄러가 주문을 만료 처리한다.
     * <p>
     * CAS(Compare-And-Set) 방식으로 PENDING_PAYMENT -> EXPIRED 상태 전이를 수행한다.
     * 상태 전이 실패(이미 다른 상태) 시 빈 Optional 반환. 재고 해제 및 장바구니 복원은 Facade에서 수행한다.
     * </p>
     *
     * @param orderId 주문 ID
     * @return 만료된 주문 엔티티 (이미 상태 전이된 경우 빈 Optional)
     */
    @Transactional
    public Optional<OrderModel> expireOrder(Long orderId) {
        int affected = orderRepository.casUpdateStatus(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED);
        if (affected == 0) {
            return Optional.empty();
        }

        OrderModel order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        return Optional.of(order);
    }

    /**
     * 해당 주문의 장바구니 복원 기록이 존재하는지 확인한다.
     *
     * @param orderId 주문 ID
     * @return 존재 여부
     */
    public boolean existsCartRestore(Long orderId) {
        return orderCartRestoreRepository.existsById(orderId);
    }

    /**
     * 장바구니 복원 기록을 저장한다.
     * <p>
     * PK 충돌 시 {@link org.springframework.dao.DataIntegrityViolationException}이 발생하여
     * 호출자가 멱등 처리를 수행할 수 있다.
     * </p>
     *
     * @param restore 복원 기록 엔티티
     */
    @Transactional
    public void saveCartRestore(OrderCartRestoreModel restore) {
        orderCartRestoreRepository.save(restore);
    }

    /**
     * 주문을 결제 완료(PAID) 상태로 전이한다.
     * <p>
     * CAS(Compare-And-Set) 방식으로 PENDING_PAYMENT → PAID 상태 전이를 수행한다.
     * 만료 스케줄러와의 레이스 컨디션을 구조적으로 방지한다.
     * </p>
     *
     * @param orderId 주문 ID
     * @return 상태 전이 성공 여부 (이미 만료/취소된 경우 false)
     */
    @Transactional
    public boolean markAsPaid(Long orderId) {
        int affected = orderRepository.casUpdateStatus(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        return affected > 0;
    }

    /**
     * 만료 시간이 지난 결제 대기(PENDING_PAYMENT) 주문 ID 목록을 조회한다.
     *
     * @return 만료 대상 주문 ID 목록
     */
    public List<Long> findExpiredPendingOrderIds() {
        return orderRepository.findExpiredPendingOrders().stream()
                .map(OrderModel::getOrderId)
                .toList();
    }

    /**
     * 주문 ID로 주문을 조회한다.
     *
     * @param orderId 주문 ID
     * @return 주문 엔티티
     * @throws CoreException 주문이 존재하지 않을 때 (ORDER_NOT_FOUND)
     */
    public OrderModel findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
    }

    /**
     * 기간별 전체 주문 목록을 조회한다 (관리자용).
     *
     * @param start 조회 시작 일시
     * @param end   조회 종료 일시
     * @return 주문 엔티티 목록
     */
    public List<OrderModel> findAllOrders(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findAllByPeriod(start, end);
    }

    /**
     * 주문 ID와 사용자 ID로 주문을 조회한다.
     *
     * @param orderId 주문 ID
     * @param userId  사용자 ID
     * @return 주문 엔티티
     * @throws CoreException 주문이 존재하지 않을 때 (ORDER_NOT_FOUND)
     */
    public OrderModel findByIdAndUserId(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
    }

    /**
     * 특정 사용자의 기간별 주문 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @param start  조회 시작 일시
     * @param end    조회 종료 일시
     * @return 주문 엔티티 목록
     */
    public List<OrderModel> findAllByUserId(Long userId, LocalDateTime start, LocalDateTime end) {
        return orderRepository.findAllByUserIdAndPeriod(userId, start, end);
    }

    /**
     * 특정 주문의 모든 주문 항목을 조회한다.
     *
     * @param orderId 주문 ID
     * @return 주문 항목 엔티티 목록
     */
    public List<OrderItemModel> findOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    /**
     * 여러 주문의 주문 항목을 일괄 조회하여 주문 ID별로 그룹핑한다.
     *
     * @param orderIds 주문 ID 목록
     * @return 주문 ID를 키로, 주문 항목 목록을 값으로 하는 맵
     */
    public Map<Long, List<OrderItemModel>> findOrderItemsByOrderIds(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItemRepository.findAllByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemModel::getOrderId));
    }

    private void validateNotEmpty(List<OrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.ORDER_ITEM_EMPTY);
        }
    }

    private List<OrderItemCommand> mergeAndSort(List<OrderItemCommand> items) {
        Map<Long, Integer> merged = items.stream()
                .collect(Collectors.groupingBy(OrderItemCommand::productId,
                        Collectors.summingInt(OrderItemCommand::quantity)));
        return merged.entrySet().stream()
                .map(e -> new OrderItemCommand(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(OrderItemCommand::productId))
                .toList();
    }

    private List<OrderItemModel> saveOrderItems(OrderModel order, Long userId,
                                                 List<OrderItemSnapshot> snapshots) {
        List<OrderItemModel> items = new ArrayList<>();
        int seq = 1;
        for (OrderItemSnapshot snapshot : snapshots) {
            items.add(OrderItemModel.create(
                    order.getOrderId(), seq++, userId,
                    snapshot.productId(), snapshot.quantity(),
                    snapshot.productName(), snapshot.unitPrice(),
                    snapshot.brandId(), snapshot.brandName(), snapshot.imageUrl(),
                    snapshot.originalAmount(), snapshot.discountAmount(), snapshot.finalAmount()));
        }
        return orderItemRepository.saveAll(items);
    }

    /**
     * DIRECT 주문 중 취소/만료 상태이고 장바구니 복원이 아직 수행되지 않은 주문 목록을 조회한다.
     * 장바구니 복원 재시도 배치에서 사용한다.
     *
     * @return 미복원 DIRECT 주문 목록
     */
    public List<OrderModel> findUnrestoredDirectOrders() {
        return orderRepository.findUnrestoredDirectOrders();
    }

    /**
     * 주문 목록을 배치 로딩으로 주문 항목을 포함하여 반환한다.
     * N+1 쿼리 대신 단일 IN 쿼리로 주문 항목을 일괄 조회한다.
     *
     * @param orders 주문 엔티티 목록
     * @return 주문 ID를 키로, 주문 항목 목록을 값으로 하는 맵이 포함된 쌍
     */
    public Map<Long, List<OrderItemModel>> batchLoadOrderItems(List<OrderModel> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(OrderModel::getOrderId).toList();
        return findOrderItemsByOrderIds(orderIds);
    }
}
