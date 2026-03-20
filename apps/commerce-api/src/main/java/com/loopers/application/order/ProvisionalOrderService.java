package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.redis.ProvisionalOrderRedisRepository;
import com.loopers.infrastructure.redis.StockReservationRedisRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 가주문(Provisional Order) 관리 서비스.
 *
 * <p>Redis CB(redis-write) Open 시 DB 직접 주문으로 Fallback.</p>
 *
 * <p>정상 경로: Redis HSET(가주문) + DECR(재고 예약)</p>
 * <p>장애 경로: DB INSERT(주문) + DB UPDATE(재고 차감)</p>
 *
 * @see <a href="06-resilience-review.md §16">가주문/진주문 설계</a>
 * @see <a href="06-resilience-review.md §16.9">DB Fallback</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisionalOrderService {

    private final ProvisionalOrderRedisRepository provisionalOrderRedisRepository;
    private final StockReservationRedisRepository stockReservationRedisRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * 가주문 생성 — Redis에 저장 + 재고 예약.
     *
     * <p>Redis 장애(redis-write CB Open) 시 {@link #saveToDbFallback}으로 전환.</p>
     */
    @CircuitBreaker(name = "redis-write", fallbackMethod = "saveToDbFallback")
    public ProvisionalOrderResult saveProvisionalOrder(Long orderId, Long memberId, int amount,
                                                        String cardType, String cardNo,
                                                        List<Order.ItemSnapshot> items) {
        // 1. Redis에 가주문 데이터 저장
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", orderId);
        orderData.put("memberId", memberId);
        orderData.put("amount", amount);
        orderData.put("cardType", cardType);
        orderData.put("cardNo", cardNo);
        orderData.put("createdAt", ZonedDateTime.now().toString());
        orderData.put("items", items.stream()
            .map(item -> Map.of(
                "productId", item.productId(),
                "quantity", item.quantity()
            )).toList());

        provisionalOrderRedisRepository.save(orderId, orderData);

        // 2. Redis 재고 예약 (DECR)
        for (Order.ItemSnapshot item : items) {
            stockReservationRedisRepository.decrease(item.productId(), item.quantity());
        }

        log.info("가주문 저장 완료 (Redis): orderId={}, memberId={}", orderId, memberId);
        return ProvisionalOrderResult.provisional(orderId);
    }

    /**
     * Redis 장애 시 DB 직접 주문 Fallback.
     *
     * <p>Redis 대신 DB에 Order(CREATED)를 직접 생성하고 DB 재고를 차감한다.</p>
     *
     * @see <a href="06-resilience-review.md §16.9">DB Fallback</a>
     */
    @Transactional
    ProvisionalOrderResult saveToDbFallback(Long orderId, Long memberId, int amount,
                                             String cardType, String cardNo,
                                             List<Order.ItemSnapshot> items, Exception e) {
        log.warn("Redis 장애 — DB 직접 주문으로 Fallback: orderId={}, error={}", orderId, e.getMessage());

        // 1. DB에 Order(CREATED) 직접 생성
        Order order = Order.create(memberId, items);
        order = orderRepository.save(order);

        // 2. DB 재고 차감
        for (Order.ItemSnapshot item : items) {
            Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                    "상품을 찾을 수 없습니다: productId=" + item.productId()));
            product.decreaseStock(item.quantity());
            productRepository.save(product);
        }

        log.info("DB 직접 주문 생성 완료: orderId={}, dbOrderId={}", orderId, order.getId());
        return ProvisionalOrderResult.directOrder(order.getId());
    }

    public Optional<Map<String, Object>> getProvisionalOrder(Long orderId) {
        return provisionalOrderRedisRepository.findByOrderId(orderId);
    }

    public void deleteProvisionalOrder(Long orderId) {
        provisionalOrderRedisRepository.deleteByOrderId(orderId);
        log.info("가주문 삭제 완료: orderId={}", orderId);
    }

    public boolean exists(Long orderId) {
        return provisionalOrderRedisRepository.exists(orderId);
    }

    /**
     * 가주문 생성 결과.
     *
     * @param orderId 주문 ID
     * @param isDirect true: DB 직접 생성 (Fallback), false: Redis 가주문
     */
    public record ProvisionalOrderResult(Long orderId, boolean isDirect) {
        public static ProvisionalOrderResult provisional(Long orderId) {
            return new ProvisionalOrderResult(orderId, false);
        }

        public static ProvisionalOrderResult directOrder(Long orderId) {
            return new ProvisionalOrderResult(orderId, true);
        }
    }
}
