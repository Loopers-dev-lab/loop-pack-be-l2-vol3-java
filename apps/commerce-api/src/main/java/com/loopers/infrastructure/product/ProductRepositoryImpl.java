package com.loopers.infrastructure.product;

import com.loopers.domain.common.cursor.CursorPageResult;
import com.loopers.domain.common.vo.RefBrandId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.infrastructure.common.cursor.CursorEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;
    private final CursorEncoder cursorEncoder;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> findByProductId(ProductId productId) {
        return productJpaRepository.findByProductId(productId);
    }

    @Override
    public boolean existsByProductId(ProductId productId) {
        return productJpaRepository.existsByProductId(productId);
    }

    @Override
    public Page<ProductModel> findProducts(Long refBrandId, String sortBy, Pageable pageable) {
        QProductModel product = QProductModel.productModel;

        BooleanExpression condition = product.deletedAt.isNull();
        if (refBrandId != null) {
            condition = condition.and(product.refBrandId.eq(new RefBrandId(refBrandId)));
        }

        ProductSortCondition sortCondition = ProductSortCondition.from(sortBy);

        List<ProductModel> content = queryFactory
                .selectFrom(product)
                .where(condition)
                .orderBy(sortCondition.toOrderSpecifiers(product))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    @Override
    public CursorPageResult<ProductModel> findProductsByCursor(
            Long refBrandId, String sortBy, String cursor, int size) {
        QProductModel product = QProductModel.productModel;
        ProductCursorCondition cursorCondition = ProductCursorCondition.from(sortBy);

        BooleanExpression baseCondition = product.deletedAt.isNull();
        if (refBrandId != null) {
            baseCondition = baseCondition.and(product.refBrandId.eq(new RefBrandId(refBrandId)));
        }

        BooleanExpression keysetCondition = null;
        if (cursor != null) {
            ProductCursor decoded = cursorEncoder.decode(cursor, ProductCursor.class);
            if (!cursorCondition.equals(ProductCursorCondition.fromType(decoded.type()))) {
                throw new CoreException(ErrorType.BAD_REQUEST, "커서와 정렬 기준이 일치하지 않습니다.");
            }
            keysetCondition = cursorCondition.toCursorPredicate(product, decoded);
        }

        List<ProductModel> content = queryFactory
                .selectFrom(product)
                .where(baseCondition, keysetCondition)
                .orderBy(cursorCondition.toOrderSpecifiers(product))
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        List<ProductModel> items = hasNext ? content.subList(0, size) : content;
        String nextCursor = hasNext
                ? cursorEncoder.encode(ProductCursor.from(items.get(items.size() - 1), sortBy))
                : null;

        return new CursorPageResult<>(items, nextCursor, hasNext, size);
    }

    @Override
    public boolean decreaseStockIfAvailable(Long productId, int quantity) {
        return productJpaRepository.decreaseStockIfAvailable(productId, quantity) > 0;
    }

    @Override
    public void increaseStock(Long productId, int quantity) {
        productJpaRepository.increaseStock(productId, quantity);
    }

    @Override
    public List<ProductModel> findByRefBrandId(Long brandId) {
        return productJpaRepository.findActiveByRefBrandId(brandId);
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findById(id);
    }
}
