package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 상품 리포지토리 구현체 (Infrastructure Layer)
 *
 * Domain 계층의 ProductRepository 포트를 구현한다.
 * QueryDSL을 사용하여 동적 쿼리를 처리하고,
 * Spring 기술 타입(PageRequest 등)을 여기서 변환하여 DIP를 준수한다.
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductMapper productMapper;
    private final JPAQueryFactory queryFactory;

    public ProductRepositoryImpl(
        ProductJpaRepository productJpaRepository,
        ProductMapper productMapper,
        JPAQueryFactory queryFactory
    ) {
        this.productJpaRepository = productJpaRepository;
        this.productMapper = productMapper;
        this.queryFactory = queryFactory;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = productMapper.toEntity(product);
        ProductEntity saved = productJpaRepository.save(entity);
        return productMapper.toDomain(saved);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id)
            .map(productMapper::toDomain);
    }

    /** 어드민 전체 상품 페이지 조회 (삭제 제외, brandId 선택 필터) */
    @Override
    public List<Product> findAll(int page, int size, Long brandId) {
        QProductEntity product = QProductEntity.productEntity;

        return queryFactory
                .selectFrom(product)
                .where(
                        product.deletedAt.isNull(),
                        brandIdEq(product, brandId)
                )
                .orderBy(product.createdAt.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch()
                .stream()
                .map(productMapper::toDomain)
                .toList();
    }

    @Override
    public long count(Long brandId) {
        QProductEntity product = QProductEntity.productEntity;

        Long count = queryFactory
                .select(product.count())
                .from(product)
                .where(
                        product.deletedAt.isNull(),
                        brandIdEq(product, brandId)
                )
                .fetchOne();

        return count != null ? count : 0L;
    }

    /** 고객 노출 가능 상품 페이지 조회 (ACTIVE, SOLDOUT만, 삭제 제외) */
    @Override
    public List<Product> findAllDisplayable(Long brandId, ProductSortType sort, int page, int size) {
        QProductEntity product = QProductEntity.productEntity;

        return queryFactory
                .selectFrom(product)
                .where(
                        product.deletedAt.isNull(),
                        product.status.in(ProductStatus.ACTIVE.name(), ProductStatus.SOLDOUT.name()),
                        brandIdEq(product, brandId)
                )
                .orderBy(toOrderSpecifier(product, sort))
                .offset((long) page * size)
                .limit(size)
                .fetch()
                .stream()
                .map(productMapper::toDomain)
                .toList();
    }

    @Override
    public long countDisplayable(Long brandId) {
        QProductEntity product = QProductEntity.productEntity;

        Long count = queryFactory
                .select(product.count())
                .from(product)
                .where(
                        product.deletedAt.isNull(),
                        product.status.in(ProductStatus.ACTIVE.name(), ProductStatus.SOLDOUT.name()),
                        brandIdEq(product, brandId)
                )
                .fetchOne();

        return count != null ? count : 0L;
    }

    /** 브랜드별 ACTIVE 상품 조회 (고객용, 삭제 제외) */
    @Override
    public List<Product> findAllActiveByBrandId(Long brandId) {
        QProductEntity product = QProductEntity.productEntity;

        return queryFactory
                .selectFrom(product)
                .where(
                        product.deletedAt.isNull(),
                        product.brandId.eq(brandId),
                        product.status.eq(ProductStatus.ACTIVE.name())
                )
                .orderBy(product.createdAt.desc())
                .fetch()
                .stream()
                .map(productMapper::toDomain)
                .toList();
    }

    /** 브랜드별 전체 상품 조회 (어드민용, 삭제 제외) */
    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        QProductEntity product = QProductEntity.productEntity;

        return queryFactory
                .selectFrom(product)
                .where(
                        product.deletedAt.isNull(),
                        product.brandId.eq(brandId)
                )
                .orderBy(product.createdAt.desc())
                .fetch()
                .stream()
                .map(productMapper::toDomain)
                .toList();
    }

    @Override
    public List<Product> findAllByIdIn(List<Long> ids) {
        return productJpaRepository.findAllById(ids)
            .stream()
            .map(productMapper::toDomain)
            .toList();
    }

    /** brandId가 null이면 필터 미적용 */
    private BooleanExpression brandIdEq(QProductEntity product, Long brandId) {
        return brandId != null ? product.brandId.eq(brandId) : null;
    }

    /** ProductSortType → QueryDSL OrderSpecifier 변환 (정렬 DIP) */
    private OrderSpecifier<?> toOrderSpecifier(QProductEntity product, ProductSortType sort) {
        if (sort == null) {
            return product.createdAt.desc();
        }
        return switch (sort) {
            case LATEST -> product.createdAt.desc();
            case PRICE_ASC -> product.basePrice.asc();
            case PRICE_DESC -> product.basePrice.desc();
            case LIKES -> product.likeCount.desc();
        };
    }
}
