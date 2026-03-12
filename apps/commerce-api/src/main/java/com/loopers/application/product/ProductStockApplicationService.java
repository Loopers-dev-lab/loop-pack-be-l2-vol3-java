package com.loopers.application.product;

import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.product.dto.OrderProductInfo;
import com.loopers.application.product.dto.ReservedProductResult;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductStockApplicationService {

    private final ProductRepository productRepository;

    @Transactional
    public List<ReservedProductResult> reserveForOrder(List<CreateOrderCommand.OrderItemCommand> items) {
        List<UUID> productIds = items.stream()
                .map(CreateOrderCommand.OrderItemCommand::productId)
                .toList();

        List<Product> products = productRepository.findAllByIdIn(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }

        for (Product product : products) {
            if (product.isDeleted()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품이 포함되어 있습니다.");
            }
        }

        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::id, product -> product));

        return items.stream()
                .map(item -> {
                    Product product = productMap.get(item.productId());
                    int updated = productRepository.decreaseStockAtomically(product.id(), item.quantity());
                    if (updated == 0) {
                        throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
                    }
                    return new ReservedProductResult(
                            product.id(),
                            item.quantity(),
                            product.name(),
                            product.price(),
                            product.brandId()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderProductInfo> getOrderProducts(List<CreateOrderCommand.OrderItemCommand> items) {
        List<UUID> productIds = items.stream()
                .map(CreateOrderCommand.OrderItemCommand::productId)
                .toList();

        List<Product> products = productRepository.findAllByIdIn(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }

        for (Product product : products) {
            if (product.isDeleted()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품이 포함되어 있습니다.");
            }
        }

        return products.stream()
                .map(product -> new OrderProductInfo(
                        product.id(),
                        product.name(),
                        product.price(),
                        product.brandId()
                ))
                .toList();
    }

    @Transactional
    public void decreaseStockForOrder(List<CreateOrderCommand.OrderItemCommand> items) {
        for (CreateOrderCommand.OrderItemCommand item : items) {
            decreaseStockWithAtomicUpdate(item.productId(), item.quantity());
        }
    }

    @Transactional
    public void decreaseStockWithAtomicUpdate(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        if (product.isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품입니다.");
        }

        int updated = productRepository.decreaseStockAtomically(productId, quantity);
        if (updated == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
    }

    @Transactional
    public void restoreForOrder(List<OrderItem> items) {
        for (OrderItem item : items) {
            productRepository.findById(item.productId()).ifPresent(product -> {
                Product restored = product.increaseStock(item.quantity());
                productRepository.save(restored);
            });
        }
    }

}
