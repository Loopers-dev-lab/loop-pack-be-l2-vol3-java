package com.loopers.infrastructure.product.repository.impl;

import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.repository.ProductCustomRepository;
import com.loopers.infrastructure.favorite.entity.QFavoriteEntity;
import com.loopers.infrastructure.product.entity.QProductEntity;
import com.loopers.support.enums.SortFilter;
import com.loopers.support.util.BooleanBuilderUtil;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.loopers.infrastructure.brand.entity.QBrandEntity.brandEntity;
import static com.loopers.infrastructure.product.entity.QProductEntity.productEntity;

@RequiredArgsConstructor
@Component
public class ProductCustomRepositoryImpl implements ProductCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ProductItem> findProductList(Long brandId, Long memberId, SortFilter sortFilter, Pageable pageable) {

        QFavoriteEntity favorite = new QFavoriteEntity("favorite");
        QFavoriteEntity myFavorite = new QFavoriteEntity("myFavorite");

        NumberExpression<Long> favoriteCnt = favorite.id.count();
        BooleanExpression isFavorite = memberId != null ? myFavorite.id.count().gt(0L) : Expressions.asBoolean(false);

        List<ProductItem> content = queryFactory
                .select(Projections.constructor(ProductItem.class,
                        productEntity.id,
                        productEntity.name,
                        brandEntity.id,
                        brandEntity.name,
                        productEntity.price,
                        productEntity.stock,
                        productEntity.displayStatus,
                        favoriteCnt,
                        isFavorite
                ))
                .from(productEntity)
                .innerJoin(brandEntity).on(productEntity.brandId.eq(brandEntity.id))
                .leftJoin(favorite).on(favorite.productId.eq(productEntity.id))
                .leftJoin(myFavorite).on(myFavorite.productId.eq(productEntity.id), myFavorite.memberId.eq(memberId != null ? memberId : 0L))
                .where(whereProductList(brandId))
                .groupBy(productEntity.id)
                .orderBy(sortFilter.toOrderSpecifier(productEntity, favoriteCnt))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory.select(productEntity.id.count())
                .from(productEntity)
                .where(whereProductList(brandId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Optional<ProductItem> findProduct(Long productId, Long memberId) {
        QFavoriteEntity favorite = new QFavoriteEntity("favorite");
        QFavoriteEntity myFavorite = new QFavoriteEntity("myFavorite");

        NumberExpression<Long> favoriteCnt = favorite.id.count();
        BooleanExpression isFavorite = memberId != null ? myFavorite.id.count().gt(0L) : Expressions.asBoolean(false);

        ProductItem result = queryFactory
                .select(Projections.constructor(ProductItem.class,
                        productEntity.id,
                        productEntity.name,
                        brandEntity.id,
                        brandEntity.name,
                        productEntity.price,
                        productEntity.stock,
                        productEntity.displayStatus,
                        favoriteCnt,
                        isFavorite
                ))
                .from(productEntity)
                .innerJoin(brandEntity).on(productEntity.brandId.eq(brandEntity.id))
                .leftJoin(favorite).on(favorite.productId.eq(productEntity.id))
                .leftJoin(myFavorite).on(myFavorite.productId.eq(productEntity.id), myFavorite.memberId.eq(memberId != null ? memberId : 0L))
                .where(
                        productEntity.id.eq(productId),
                        productEntity.deletedAt.isNull()
                )
                .groupBy(productEntity.id)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    private BooleanBuilder whereProductList(Long brandId) {
        return new BooleanBuilder(productEntity.deletedAt.isNull())
                .and(BooleanBuilderUtil.nullSafeBuilder(() -> productEntity.brandId.eq(brandId)));
    }
}
