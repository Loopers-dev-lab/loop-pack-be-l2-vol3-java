package com.loopers.application.order;

import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public Order create(CreateOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<Long> productIds = command.items().stream()
                .map(CreateOrderCommand.OrderItemCommand::productId)
                .toList();

        // 비관적 락으로 상품 조회 (재고 차감 동시성 보호)
        List<Product> products = productRepository.findAllByIdInWithLock(productIds);

        // 존재하지 않는 상품 확인
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::id, p -> p));

        // 삭제된 상품 확인
        for (Product p : products) {
            if (p.isDeleted()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품이 포함되어 있습니다.");
            }
        }

        // 브랜드 조회 (스냅샷용)
        Map<Long, Brand> brandMap = products.stream()
                .map(Product::brandId)
                .distinct()
                .map(brandId -> brandRepository.findById(brandId)
                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")))
                .collect(Collectors.toMap(b -> b.id(), b -> b));

        // 재고 차감 및 OrderItem 생성
        List<OrderItem> orderItems = new ArrayList<>();
        for (CreateOrderCommand.OrderItemCommand itemCmd : command.items()) {
            Product product = productMap.get(itemCmd.productId());
            Product updated = product.decreaseStock(itemCmd.quantity());
            productRepository.save(updated);

            Brand brand = brandMap.get(product.brandId());
            orderItems.add(new OrderItem(
                    product.id(),
                    itemCmd.quantity(),
                    product.name(),
                    product.price(),
                    brand.name().value()
            ));
        }

        String orderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        Order order = new Order(command.userId(), orderNumber, orderItems);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancel(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!isAdmin && !order.isOwner(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "타인의 주문을 취소할 수 없습니다.");
        }

        Order cancelled = order.cancel(); // 이미 취소된 경우 409 CONFLICT 던짐

        // 재고 복원
        for (OrderItem item : order.items()) {
            productRepository.findById(item.productId()).ifPresent(product -> {
                Product restored = product.increaseStock(item.quantity());
                productRepository.save(restored);
            });
        }

        return orderRepository.save(cancelled);
    }

    @Transactional(readOnly = true)
    public Order getById(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!isAdmin && !order.isOwner(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "타인의 주문을 조회할 수 없습니다.");
        }

        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> listByUser(Long userId, LocalDate startAt, LocalDate endAt, Pageable pageable) {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime startDateTime = startAt.atStartOfDay(kst);
        ZonedDateTime endDateTime = endAt.atTime(23, 59, 59).atZone(kst);
        return orderRepository.findByUserId(userId, startDateTime, endDateTime, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> listAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
