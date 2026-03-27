package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.QProductModel;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public int incrementLikeCount(Long productId) {
        return productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public int decrementLikeCount(Long productId) {
        return productJpaRepository.decrementLikeCount(productId);
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public Page<ProductModel> findAll(Long brandId, Pageable pageable, ProductSortType sortType) {
        QProductModel product = QProductModel.productModel;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.deletedAt.isNull());
        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }

        OrderSpecifier<?> orderSpecifier = toOrderSpecifier(product, sortType);

        List<ProductModel> content = queryFactory.selectFrom(product)
            .where(where)
            .orderBy(orderSpecifier)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory.select(product.count())
            .from(product)
            .where(where)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    private OrderSpecifier<?> toOrderSpecifier(QProductModel product, ProductSortType sortType) {
        return switch (sortType) {
            case LATEST, CREATED_DESC -> product.createdAt.desc();
            case PRICE_ASC -> product.price.value.asc();
            case PRICE_DESC -> product.price.value.desc();
            case LIKES_DESC -> product.likeCount.desc();
        };
    }

    @Override
    public List<ProductModel> findAllByIdInAndDeletedAtIsNull(List<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }
}
