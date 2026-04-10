package com.loopers.domain.product;

import com.loopers.domain.product.dto.ProductCommand;
import com.loopers.domain.product.dto.ProductInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    @Transactional
    public void register(Long brandId, String name, int price, int stock) {
        productRepository.save(ProductModel.create(brandId, name, price, stock));
    }

    @Transactional(readOnly = true)
    public ProductModel getById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ProductErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllByIds(List<Long> ids) {
        List<ProductModel> products = productRepository.findAllByIdIn(ids);
        if (products.size() != ids.size()) {
            throw new CoreException(ProductErrorCode.NOT_FOUND);
        }
        return products;
    }

    @Transactional(readOnly = true)
    public List<ProductModel> findAllByIds(List<Long> ids) {
        return productRepository.findAllByIdIn(ids);
    }

    @Transactional
    public void update(Long id, String name, int price, int stock) {
        getById(id).update(name, price, stock);
    }

    @Transactional
    public void delete(Long id) {
        getById(id).delete();
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAllByBrandId(Long brandId, Pageable pageable) {
        return productRepository.findAllByBrandId(brandId, pageable);
    }

    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        productRepository.findAllByBrandId(brandId).forEach(ProductModel::delete);
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return productRepository.findById(id).isPresent();
    }

    @Transactional(readOnly = true)
    public void validateExists(Long id) {
        if (!existsById(id)) {
            throw new CoreException(ProductErrorCode.NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllByBrandId(Long brandId) {
        return productRepository.findAllByBrandId(brandId);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAllSortedByLikeCountDesc(Pageable pageable) {
        return productRepository.findAllSortedByLikeCountDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAllByBrandIdSortedByLikeCountDesc(Long brandId, Pageable pageable) {
        return productRepository.findAllByBrandIdSortedByLikeCountDesc(brandId, pageable);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getLikeCountsByProductIds(List<Long> productIds) {
        return productRepository.findLikeCountsByProductIds(productIds);
    }

    @Transactional(readOnly = true)
    public long getLikeCountByProductId(Long productId) {
        return productRepository.findLikeCountByProductId(productId);
    }

    @Transactional
    public void increaseStock(Long productId, int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복구 수량은 1 이상이어야 합니다.");
        }
        productRepository.increaseStock(productId, quantity);
    }

    @Transactional
    public List<ProductInfo.StockDeduction> validateAndDeductStock(
            List<ProductCommand.StockDeduction> commands) {
        List<ProductModel> products = getAllByIds(
                commands.stream().map(ProductCommand.StockDeduction::productId).toList());

        Map<Long, ProductModel> productMap = products.stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<ProductInfo.StockDeduction> results = commands.stream()
                .map(command -> {
                    ProductModel product = productMap.get(command.productId());
                    product.validateExpectedPrice(command.expectedPrice());
                    return new ProductInfo.StockDeduction(
                            command.productId(),
                            product.getName(),
                            product.getPrice(),
                            command.quantity(),
                            product.getBrandId());
                })
                .toList();

        for (ProductCommand.StockDeduction command : commands) {
            if (command.quantity() < 1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
            }
            int updated = productRepository.decreaseStock(command.productId(), command.quantity());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
            }
        }

        return results;
    }
}
