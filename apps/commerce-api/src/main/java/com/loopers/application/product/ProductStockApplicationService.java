package com.loopers.application.product;

import com.loopers.application.order.command.CreateOrderCommand;
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
    public List<ReservedProduct> reserveForOrder(List<CreateOrderCommand.OrderItemCommand> items) {
        List<UUID> productIds = items.stream()
                .map(CreateOrderCommand.OrderItemCommand::productId)
                .toList();

        List<Product> products = productRepository.findAllByIdInWithLock(productIds);
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
                    Product updated = product.decreaseStock(item.quantity());
                    productRepository.save(updated);
                    return new ReservedProduct(
                            product.id(),
                            item.quantity(),
                            product.name(),
                            product.price(),
                            product.brandId()
                    );
                })
                .toList();
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

    public record ReservedProduct(
            UUID productId,
            int quantity,
            String productName,
            int productPrice,
            UUID brandId
    ) {
    }
}
