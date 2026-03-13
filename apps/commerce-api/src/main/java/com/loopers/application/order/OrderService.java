package com.loopers.application.order;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderDomainService.OrderLineRequest;
import com.loopers.domain.order.OrderLine;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderDomainService orderDomainService;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    /**
     * 주문 처리: 쿠폰 검증 → 재고 차감 → 주문 생성을 하나의 트랜잭션으로 묶는다.
     *
     * ─── 트랜잭션 범위 설계 ────────────────────────────────────────────────────
     * 트랜잭션 범위를 크게 잡으면 락 보유 시간이 길어져 처리량(throughput)이 낮아진다.
     * 트랜잭션 범위를 작게 잡으면 원자성이 깨질 수 있다.
     *
     * 이 메서드가 트랜잭션 경계를 담당하는 이유:
     * 1. 재고 차감, 쿠폰 사용, 주문 생성은 하나라도 실패하면 모두 롤백해야 한다.
     *    → 세 작업이 반드시 같은 트랜잭션 안에 있어야 한다.
     * 2. 단순 검증(쿠폰 만료 여부, 소유자 확인)은 상태를 변경하지 않는다.
     *    → 가능하면 락 획득 전 또는 트랜잭션 시작 직후에 처리해 락 보유 시간을 줄인다.
     *
     * ─── 락 획득 순서와 데드락 방지 ──────────────────────────────────────────
     * 쿠폰 락과 재고 락이 동시에 필요할 때, 획득 순서가 일정하지 않으면 데드락 발생 가능.
     *
     * 설계 원칙: 쿠폰 락 → 상품 락 순서를 항상 유지한다.
     *   - 쿠폰은 1건이므로 먼저 획득한다.
     *   - 상품은 여러 개일 수 있으므로, productId 오름차순으로 순차 획득한다.
     *     (OrderDomainService.prepareOrderLines 내부에서 처리)
     *
     * 이 순서를 모든 트랜잭션에서 동일하게 유지하면, 순환 대기 조건이 성립하지 않아
     * 데드락이 발생하지 않는다.
     *
     * ─── 쿠폰: 비관적 락 선택 근거 ──────────────────────────────────────────
     * 쿠폰은 "단 1회만 사용 가능"이라는 엄격한 제약이 있다.
     * 같은 쿠폰으로 여러 기기에서 동시에 주문을 시도하면 Lost Update 문제가 발생한다:
     *   Thread 1: coupon 조회(status=AVAILABLE) → Thread 2: coupon 조회(status=AVAILABLE)
     *   → 둘 다 사용 가능하다고 판단 → 둘 다 USED로 변경 → 중복 사용
     *
     * findByIdForUpdate로 조회 시 DB 레벨 행 잠금 획득.
     * Thread 2는 Thread 1의 트랜잭션이 끝날 때까지 대기하고,
     * Thread 1 커밋 후 Thread 2가 읽으면 status=USED → 예외 발생 → 중복 사용 방지.
     *
     * 낙관적 락(@Version) 고려:
     * 가능은 하지만, 쿠폰 중복 사용은 "실패"보다 "방지"가 우선이다.
     * OptimisticLockException 후 재시도 시 사용 가능한 쿠폰이 없으면 결국 실패이므로
     * 비관적 락이 더 명확한 의도를 드러낸다.
     */
    @Transactional
    public OrderResult placeOrder(Long memberId, List<OrderLineRequest> items, Long couponId) {
        IssuedCoupon issuedCoupon = null;
        CouponTemplate couponTemplate = null;

        if (couponId != null) {
            // 1단계: 쿠폰 비관적 락 획득 (SELECT FOR UPDATE)
            //        이 시점 이후로 다른 트랜잭션은 같은 쿠폰 행 수정 불가
            issuedCoupon = issuedCouponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

            // 소유자 검증: 도메인 메서드에 위임 (비즈니스 규칙 캡슐화)
            issuedCoupon.validateOwnership(memberId);

            // 사용 가능 상태 확인: USED 또는 EXPIRED면 주문 거부
            if (!issuedCoupon.isUsable()) {
                throw new CoreException(ErrorType.COUPON_UNAVAILABLE);
            }

            // 쿠폰 템플릿 조회: 할인 계산에 필요 (락 불필요 - 읽기 전용)
            couponTemplate = couponTemplateRepository.findById(issuedCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

            if (couponTemplate.isExpired()) {
                throw new CoreException(ErrorType.COUPON_EXPIRED);
            }
        }

        // 2단계: 재고 차감 (productId 오름차순 비관적 락 획득)
        //        데드락 방지를 위해 OrderDomainService 내부에서 정렬 후 처리
        List<OrderLine> orderLines = orderDomainService.prepareOrderLines(items);

        // 3단계: 할인 계산 및 주문 생성
        //        쿠폰 실제 사용 처리(use())는 주문 생성과 함께 묶어서 원자성 보장
        //        최소 주문 금액 검증도 이 시점에 실행
        long originalAmount = orderLines.stream().mapToLong(OrderLine::getTotalPrice).sum();

        Order order;
        if (issuedCoupon != null) {
            couponTemplate.validateMinOrderAmount(originalAmount);
            long discountAmount = couponTemplate.calculateDiscount(originalAmount);
            order = orderDomainService.createOrderWithCoupon(memberId, orderLines, couponId, discountAmount);

            // 쿠폰 상태 변경: dirty checking으로 트랜잭션 커밋 시 자동 UPDATE
            // 이 시점에 issuedCoupon은 영속성 컨텍스트에 존재하므로 별도 save() 불필요
            issuedCoupon.use();
        } else {
            order = orderDomainService.createOrder(memberId, orderLines);
        }

        List<OrderLineInfo> resultLines = order.getOrderLines().stream()
            .map(ol -> new OrderLineInfo(ol.getProductId(), ol.getQuantity(), ol.getUnitPrice()))
            .collect(Collectors.toList());

        return new OrderResult(
            order.getId(), order.getStatus(),
            order.getOriginalAmount(), order.getDiscountAmount(), order.getTotalAmount(),
            resultLines
        );
    }

    public record OrderResult(
        Long orderId,
        String status,
        long originalAmount,
        long discountAmount,
        long totalAmount,
        List<OrderLineInfo> orderLines
    ) {}

    public record OrderLineInfo(Long productId, int quantity, long unitPrice) {}
}
