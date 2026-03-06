package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductService {
    private final ProductRepository productRepository;

    @Transactional
    public ProductInfo register(ProductCreateCommand command) {
        Product product = Product.create(command.brandId(), command.name(), command.description(), command.price(), command.stockQuantity());
        return ProductInfo.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductInfo getActiveProduct(Long id) {
        Product product = findById(id);
        if (!product.isActive()) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 를 찾을 수 없습니다.");
        }

        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        Product product = findById(id);
        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 를 찾을 수 없습니다.");
        }

        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getActiveProducts(Long brandId, ProductSort sort, Pageable pageable) {
        return productRepository.findActiveProducts(brandId, sort.toOrder(), pageable).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(Long brandId, Pageable pageable) {
        return productRepository.findAllProducts(brandId, pageable).map(ProductInfo::from);
    }

    @Transactional
    public ProductInfo update(Long id, ProductUpdateCommand command) {
        Product product = findById(id);
        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 를 찾을 수 없습니다.");
        }

        product.update(command.name(), command.description(), command.price(), command.stockQuantity());
        if (command.visibility() != null) {
            product.changeVisibility(command.visibility());
        }

        return ProductInfo.from(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.delete();
    }

    @Transactional(readOnly = true)
    public List<Long> getProductIdsByBrandId(Long brandId) {
        return productRepository.findAllByBrandIdAndDeletedAtIsNull(brandId)
                                .stream()
                                .map(Product::getId)
                                .toList();
    }

    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
        products.forEach(Product::delete);
    }

    @Transactional
    public void increaseLikeCount(Long productId) {
        Product product = findById(productId);
        if (!product.isActive()) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 를 찾을 수 없습니다.");
        }

        productRepository.increaseLikeCount(productId);
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        Product product = findById(productId);
        if (!product.isActive()) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 를 찾을 수 없습니다.");
        }

        productRepository.decreaseLikeCount(productId);
    }

    @Transactional
    public void decreaseStock(List<OrderItemCommand> items) {
        List<OrderItemCommand> sorted = items.stream()
                                             .sorted(Comparator.comparing(OrderItemCommand::productId))
                                             .toList();

        for (OrderItemCommand item : sorted) {
            boolean decreased = productRepository.decreaseStockIfEnough(item.productId(), item.quantity());
            if (!decreased) {
                throw new CoreException(ErrorType.INSUFFICIENT_STOCK, "재고가 부족한 상품이 있습니다.");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getActiveProductsByIds(List<Long> ids) {
        return productRepository.findAllByIdInAndDeletedAtIsNull(ids)
                                .stream()
                                .filter(Product::isActive)
                                .map(ProductInfo::from)
                                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getActiveProductsByIdsOrThrow(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<ProductInfo> products = getActiveProductsByIds(distinctIds);

        if (products.size() != distinctIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문 상품이 올바르지 않습니다.");
        }

        return products;
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                        "[productId = " + id + "] 를 찾을 수 없습니다."));
    }
}
