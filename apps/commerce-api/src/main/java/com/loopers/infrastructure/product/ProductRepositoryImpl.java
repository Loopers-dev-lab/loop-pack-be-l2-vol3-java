package com.loopers.infrastructure.product;

import static com.loopers.infrastructure.product.QProductEntity.productEntity;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Product save(Product product) {
        ProductEntity productEntity = ProductEntity.create(product);

        return ProductEntity.toDomain(productJpaRepository.save(productEntity));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id)
            .map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findByIds(List<Long> ids) {
        return productJpaRepository.findAllById(ids).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public Product update(Product product) {
        ProductEntity productEntity = productJpaRepository.findById(product.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Product.PRODUCT_NOT_FOUND));
        productEntity.update(product);

        return ProductEntity.toDomain(productJpaRepository.save(productEntity));
    }

    @Override
    public List<Product> findAll(ProductSearchCondition condition) {
        return queryFactory
            .selectFrom(productEntity)
            .where(
                condition.hasBrandId() ? productEntity.refBrandId.eq(condition.brandId()) : null
            )
            .orderBy(getOrderSpecifier(condition.sortType()))
            .offset((long) condition.page() * condition.size())
            .limit(condition.size())
            .fetch()
            .stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    private OrderSpecifier<?> getOrderSpecifier(ProductSortType sortType) {
        return switch (sortType) {
            case LATEST -> productEntity.createdAt.desc();
            case PRICE_ASC -> productEntity.price.asc();
            case LIKES_DESC -> productEntity.likeCount.desc();
        };
    }
}
