package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductOrder;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Page<Product> findActiveProducts(Long brandId, ProductOrder order, Pageable pageable) {
        QProduct product = QProduct.product;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(product.deletedAt.isNull());
        builder.and(product.visibility.eq(Product.Visibility.VISIBLE));

        if (brandId != null) {
            builder.and(product.brandId.eq(brandId));
        }

        OrderSpecifier<?> orderSpecifier = switch (order) {
            case PRICE_ASC -> product.price.asc();
            case LIKES_DESC -> product.likeCount.desc();
            case LATEST -> product.id.desc();
        };

        List<Product> content = queryFactory
                .selectFrom(product)
                .where(builder)
                .orderBy(orderSpecifier)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    @Override
    public Page<Product> findAllProducts(Long brandId, Pageable pageable) {
        QProduct product = QProduct.product;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(product.deletedAt.isNull());

        if (brandId != null) {
            builder.and(product.brandId.eq(brandId));
        }

        List<Product> content = queryFactory
                .selectFrom(product)
                .where(builder)
                .orderBy(product.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    @Override
    public List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public boolean decreaseStockIfEnough(Long productId, Integer quantity) {
        return productJpaRepository.decreaseStockIfEnough(productId, quantity, Product.Visibility.VISIBLE) > 0;
    }
}
