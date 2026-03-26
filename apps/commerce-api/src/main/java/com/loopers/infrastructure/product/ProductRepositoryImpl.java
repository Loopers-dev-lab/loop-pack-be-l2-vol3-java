package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductOrder;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
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
    @PersistenceContext
    private EntityManager entityManager;

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
        if (order == ProductOrder.LIKES_DESC) {
            return findActiveProductsOrderByLikes(brandId, pageable);
        }

        QProduct product = QProduct.product;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(product.deletedAt.isNull());
        builder.and(product.visibility.eq(Product.Visibility.VISIBLE));

        if (brandId != null) {
            builder.and(product.brandId.eq(brandId));
        }

        OrderSpecifier<?> orderSpecifier = switch (order) {
            case PRICE_ASC -> product.price.asc();
            case LATEST -> product.id.desc();
        case LIKES_DESC -> throw new IllegalStateException("LIKES_DESC는 별도 메서드로 처리");
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

    private Page<Product> findActiveProductsOrderByLikes(Long brandId, Pageable pageable) {
        String where = "WHERE p.deleted_at IS NULL AND p.visibility = 'VISIBLE'";
        if (brandId != null) {
            where += " AND p.brand_id = :brandId";
        }

        String sql = """
                SELECT p.* FROM product p
                LEFT JOIN product_metrics pm ON pm.product_id = p.id
                %s
                ORDER BY COALESCE(pm.like_count, 0) DESC
                LIMIT :limit OFFSET :offset
                """.formatted(where);

        String countSql = "SELECT COUNT(*) FROM product p " + where;

        Query query = entityManager.createNativeQuery(sql, Product.class)
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", (int) pageable.getOffset());

        Query countQuery = entityManager.createNativeQuery(countSql);

        if (brandId != null) {
            query.setParameter("brandId", brandId);
            countQuery.setParameter("brandId", brandId);
        }

        @SuppressWarnings("unchecked")
        List<Product> content = query.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, pageable, total);
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
        return productJpaRepository.decreaseStockIfEnough(productId, quantity) > 0;
    }

    @Override
    public int increaseStock(Long productId, Integer quantity) {
        return productJpaRepository.increaseStock(productId, quantity);
    }

}
