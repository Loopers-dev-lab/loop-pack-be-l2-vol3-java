package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortOrder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.loopers.domain.product.QProductModel.productModel;
import static com.loopers.domain.like.QLikeModel.likeModel;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository, JPAQueryFactory queryFactory) {
        this.productJpaRepository = productJpaRepository;
        this.queryFactory = queryFactory;
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Optional<ProductModel> findByIdAndNotDeleted(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<ProductModel> findNotDeleted(ProductSortOrder sortOrder, Long brandId, Pageable pageable) {
        if (sortOrder == ProductSortOrder.LIKES_DESC) {
            return findNotDeletedOrderByLikesDesc(brandId, pageable);
        }
        BooleanExpression brandCond = brandId != null ? productModel.brandId.eq(brandId) : null;
        var query = queryFactory.selectFrom(productModel)
            .where(productModel.deletedAt.isNull(), brandCond);
        OrderSpecifier<?> order = orderBy(sortOrder);
        List<ProductModel> content = query.orderBy(order)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        long total = Optional.ofNullable(
            queryFactory.select(productModel.count())
                .from(productModel)
                .where(productModel.deletedAt.isNull(), brandCond)
                .fetchOne()).orElse(0L);
        return new PageImpl<>(content, pageable, total);
    }

    private OrderSpecifier<?> orderBy(ProductSortOrder sortOrder) {
        return switch (sortOrder) {
            case LATEST -> productModel.createdAt.desc();
            case PRICE_ASC -> productModel.price.asc();
            case PRICE_DESC -> productModel.price.desc();
            default -> productModel.createdAt.desc();
        };
    }

    private Page<ProductModel> findNotDeletedOrderByLikesDesc(Long brandId, Pageable pageable) {
        BooleanExpression brandCond = brandId != null ? productModel.brandId.eq(brandId) : null;
        List<ProductModel> content = queryFactory.selectFrom(productModel)
            .leftJoin(likeModel).on(likeModel.productId.eq(productModel.id))
            .where(productModel.deletedAt.isNull(), brandCond)
            .groupBy(productModel.id, productModel.brandId, productModel.name, productModel.price,
                productModel.stockQuantity, productModel.createdAt, productModel.updatedAt, productModel.deletedAt)
            .orderBy(likeModel.id.count().desc(), productModel.id.asc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        long total = Optional.ofNullable(
            queryFactory.select(productModel.countDistinct())
                .from(productModel)
                .where(productModel.deletedAt.isNull(), brandCond)
                .fetchOne()).orElse(0L);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<ProductModel> findByBrandIdAndNotDeleted(Long brandId) {
        return productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public Optional<ProductModel> findByIdForUpdate(Long id) {
        return productJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }
}
