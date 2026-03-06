package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderDomainService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    /**
     * 재고를 차감하고 주문 라인 목록을 반환한다.
     *
     * ─── 비관적 락(Pessimistic Lock) 선택 근거 ───────────────────────────────
     * 재고 차감은 "읽기 → 조건 확인 → 쓰기"가 하나의 원자 단위여야 한다.
     *
     * 낙관적 락(@Version)은 "일단 읽고, 커밋 시점에 충돌을 감지"하는 방식이다.
     * 재고처럼 동시 경합이 높은 상황에서 낙관적 락을 사용하면:
     *   - 여러 스레드가 동시에 같은 stock 값을 읽어 조건을 통과한다.
     *   - 커밋 시점에 OptimisticLockException이 발생해 롤백된다.
     *   - Application Layer에서 재시도 로직이 필요해 복잡도가 높아진다.
     *   - 재고 10개인 상품에 10명이 동시 주문하면 대부분 실패 → 재시도 지옥
     *
     * 비관적 락(SELECT ... FOR UPDATE)은 조회 시점에 DB 행 잠금을 획득해
     * 다른 트랜잭션이 커밋 전까지 해당 행을 수정하지 못하게 막는다.
     * 충돌이 잦은 경우 재시도 없이도 순차 처리가 보장된다.
     *
     * ─── 데드락(Deadlock) 방지 전략 ──────────────────────────────────────────
     * 여러 상품을 한 번에 주문할 때, 트랜잭션 간 락 획득 순서가 다르면 데드락이 발생한다.
     *
     * 시나리오 예시:
     *   Thread A: product(id=1) 락 획득 → product(id=2) 락 대기
     *   Thread B: product(id=2) 락 획득 → product(id=1) 락 대기
     *   → 서로를 기다리는 순환 대기 → 데드락 발생
     *
     * 해결책: 모든 트랜잭션이 productId 오름차순으로 락을 획득하도록 강제한다.
     * 락 요청 순서가 전역적으로 일치하면 순환 대기 조건이 성립하지 않는다.
     */
    public List<OrderLine> prepareOrderLines(List<OrderLineRequest> items) {
        // 데드락 방지: productId 오름차순으로 정렬 후 순차적으로 락 획득
        List<OrderLineRequest> sorted = items.stream()
            .sorted((a, b) -> Long.compare(a.productId(), b.productId()))
            .toList();

        List<OrderLine> orderLines = new ArrayList<>();
        for (OrderLineRequest item : sorted) {
            // SELECT ... FOR UPDATE: 해당 상품 행에 독점 락 획득
            // 락을 보유한 트랜잭션이 커밋 또는 롤백할 때까지 다른 트랜잭션은 대기
            Product product = productRepository.findByIdForUpdate(item.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                    "[id = " + item.productId() + "] 상품을 찾을 수 없습니다."));

            // 재고 음수 방지 검증은 Product 도메인 내부에 캡슐화
            // Application Layer가 재고 규칙을 직접 알지 않아도 된다.
            product.decreaseStock(item.quantity());
            orderLines.add(new OrderLine(item.productId(), item.quantity(), product.getPrice()));
        }
        return orderLines;
    }

    public Order createOrder(Long memberId, List<OrderLine> orderLines) {
        Order order = Order.create(memberId, orderLines);
        return orderRepository.save(order);
    }

    public Order createOrderWithCoupon(Long memberId, List<OrderLine> orderLines, Long couponId, long discountAmount) {
        Order order = Order.createWithCoupon(memberId, orderLines, couponId, discountAmount);
        return orderRepository.save(order);
    }

    public record OrderLineRequest(Long productId, int quantity) {}
}
