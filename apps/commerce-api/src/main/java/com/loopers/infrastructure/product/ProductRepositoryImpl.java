package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.common.CursorResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.Duration;
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

    private static final String ACTIVE_BRAND_IDS_KEY = "activeBrandIds";

    private final ProductJpaRepository productJpaRepository;
    private final ProductMapper productMapper;
    private final JPAQueryFactory queryFactory;
    private final BrandJpaRepository brandJpaRepository;

    private final LoadingCache<String, List<Long>> activeBrandIdsCache;

    public ProductRepositoryImpl(
        ProductJpaRepository productJpaRepository,
        ProductMapper productMapper,
        JPAQueryFactory queryFactory,
        BrandJpaRepository brandJpaRepository
    ) {
        this.productJpaRepository = productJpaRepository;
        this.productMapper = productMapper;
        this.queryFactory = queryFactory;
        this.brandJpaRepository = brandJpaRepository;

        this.activeBrandIdsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(1)
                .build(key -> loadActiveBrandIds());
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

    @Override
    public CursorResult<Product> findAllDisplayableWithCursor(Long brandId, ProductSortType sort, ProductCursor cursor, int size) {
        QProductEntity product = QProductEntity.productEntity;

        List<Long> activeBrandIds = getActiveBrandIds();

        var query = queryFactory
                .selectFrom(product)
                .where(
                        product.deletedAt.isNull(),
                        product.status.in(ProductStatus.ACTIVE, ProductStatus.SOLDOUT),
                        product.brandId.in(activeBrandIds),
                        brandIdEq(product, brandId),
                        cursorCondition(product, sort, cursor)
                )
                .orderBy(toOrderSpecifiers(product, sort))
                .limit(size + 1);

        List<Product> fetched = query.fetch()
                .stream()
                .map(productMapper::toDomain)
                .toList();

        return CursorResult.of(fetched, size);
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
                        product.status.eq(ProductStatus.ACTIVE)
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

    /** 좋아요 목록용: ID 목록으로 상품 조회 (브랜드 ACTIVE + 미삭제 필터) */
    @Override
    public List<Product> findAllByIdIn(List<Long> ids) {
        QProductEntity product = QProductEntity.productEntity;

        List<Long> activeBrandIds = getActiveBrandIds();

        return queryFactory
                .selectFrom(product)
                .where(
                        product.id.in(ids),
                        product.deletedAt.isNull(),
                        product.brandId.in(activeBrandIds)
                )
                .fetch()
                .stream()
                .map(productMapper::toDomain)
                .toList();
    }

    @Override
    public int incrementLikeCount(Long id) {
        return productJpaRepository.incrementLikeCount(id);
    }

    @Override
    public int decrementLikeCount(Long id) {
        return productJpaRepository.decrementLikeCount(id);
    }

    private List<Long> getActiveBrandIds() {
        return activeBrandIdsCache.get(ACTIVE_BRAND_IDS_KEY);
    }

    private List<Long> loadActiveBrandIds() {
        return brandJpaRepository.findAllByStatusAndDeletedAtIsNull(BrandStatus.ACTIVE)
                .stream()
                .map(BrandEntity::getId)
                .toList();
    }

    /** brandId가 null이면 필터 미적용 */
    private BooleanExpression brandIdEq(QProductEntity product, Long brandId) {
        return brandId != null ? product.brandId.eq(brandId) : null;
    }

    /** ProductSortType → QueryDSL OrderSpecifier 배열 (정렬키 + id tie-breaking) */
    private OrderSpecifier<?>[] toOrderSpecifiers(QProductEntity product, ProductSortType sort) {
        ProductSortType effectiveSort = sort != null ? sort : ProductSortType.LATEST;
        return switch (effectiveSort) {
            case LATEST -> new OrderSpecifier[]{product.createdAt.desc(), product.id.desc()};
            case PRICE_ASC -> new OrderSpecifier[]{product.basePrice.asc(), product.id.asc()};
            case PRICE_DESC -> new OrderSpecifier[]{product.basePrice.desc(), product.id.desc()};
            case LIKES_DESC -> new OrderSpecifier[]{product.likeCount.desc(), product.id.desc()};
        };
    }

    /** 커서 조건: (sortKey, id) 기반 WHERE 절 생성 */
    private BooleanExpression cursorCondition(QProductEntity product, ProductSortType sort, ProductCursor cursor) {
        if (cursor == null) {
            return null;
        }
        ProductSortType effectiveSort = sort != null ? sort : ProductSortType.LATEST;
        return switch (effectiveSort) {
            case LATEST -> product.createdAt.lt(cursor.createdAt())
                    .or(product.createdAt.eq(cursor.createdAt()).and(product.id.lt(cursor.id())));
            case PRICE_ASC -> product.basePrice.gt(cursor.basePrice())
                    .or(product.basePrice.eq(cursor.basePrice()).and(product.id.gt(cursor.id())));
            case PRICE_DESC -> product.basePrice.lt(cursor.basePrice())
                    .or(product.basePrice.eq(cursor.basePrice()).and(product.id.lt(cursor.id())));
            case LIKES_DESC -> product.likeCount.lt(cursor.likeCount())
                    .or(product.likeCount.eq(cursor.likeCount()).and(product.id.lt(cursor.id())));
        };
    }
}
