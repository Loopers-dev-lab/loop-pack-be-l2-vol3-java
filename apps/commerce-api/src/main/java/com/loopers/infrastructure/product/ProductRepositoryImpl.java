package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.query.ProductCursor;
import com.loopers.domain.product.query.ProductCursorPage;
import com.loopers.domain.product.query.ProductListCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final EntityManager entityManager;

    @Override
    public Product save(Product product) {
        if (product.id() != null) {
            return productJpaRepository.findByReferenceId(product.id())
                    .map(entity -> {
                        entity.updateFrom(product);
                        if (product.deletedAt() != null) {
                            entity.delete();
                        }
                        return productJpaRepository.save(entity).toDomain();
                    })
                    .orElseGet(() -> productJpaRepository.save(ProductEntity.from(product)).toDomain());
        }
        ProductEntity entity = ProductEntity.from(product);
        ProductEntity saved = productJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return productJpaRepository.findByReferenceIdAndDeletedAtIsNull(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findAllByIdIn(List<UUID> ids) {
        return productJpaRepository.findAllByReferenceIdInAndDeletedAtIsNullOrderByIdAsc(ids)
                .stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Product> findByIdIncludingDeleted(UUID id) {
        return productJpaRepository.findByReferenceId(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public ProductCursorPage searchByCursor(ProductListCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ProductEntity> query = cb.createQuery(ProductEntity.class);
        Root<ProductEntity> root = query.from(ProductEntity.class);

        Predicate predicate = ProductSpecifications.from(criteria).toPredicate(root, query, cb);
        if (criteria.cursor() != null) {
            ProductCursor cursor = criteria.cursor();
            Predicate cursorPredicate = switch (criteria.sortOption()) {
                case LIKES -> cb.or(
                        cb.lessThan(root.get("likeCount"), Integer.parseInt(cursor.primaryValue())),
                        cb.and(
                                cb.equal(root.get("likeCount"), Integer.parseInt(cursor.primaryValue())),
                                cb.lessThan(root.get("id"), cursor.id())
                        )
                );
                case LATEST -> cb.or(
                        cb.lessThan(root.get("createdAt"), java.time.ZonedDateTime.parse(cursor.primaryValue())),
                        cb.and(
                                cb.equal(root.get("createdAt"), java.time.ZonedDateTime.parse(cursor.primaryValue())),
                                cb.lessThan(root.get("id"), cursor.id())
                        )
                );
                case PRICE -> cb.or(
                        cb.greaterThan(root.get("price"), Integer.parseInt(cursor.primaryValue())),
                        cb.and(
                                cb.equal(root.get("price"), Integer.parseInt(cursor.primaryValue())),
                                cb.greaterThan(root.get("id"), cursor.id())
                        )
                );
                case NAME -> cb.or(
                        cb.greaterThan(root.get("name"), cursor.primaryValue()),
                        cb.and(
                                cb.equal(root.get("name"), cursor.primaryValue()),
                                cb.greaterThan(root.get("id"), cursor.id())
                        )
                );
            };
            predicate = cb.and(predicate, cursorPredicate);
        }

        query.where(predicate);
        switch (criteria.sortOption()) {
            case LIKES -> query.orderBy(cb.desc(root.get("likeCount")), cb.desc(root.get("id")));
            case LATEST -> query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
            case PRICE -> query.orderBy(cb.asc(root.get("price")), cb.asc(root.get("id")));
            case NAME -> query.orderBy(cb.asc(root.get("name")), cb.asc(root.get("id")));
        }

        TypedQuery<ProductEntity> typedQuery = entityManager.createQuery(query);
        typedQuery.setMaxResults(criteria.size() + 1);
        List<ProductEntity> fetched = typedQuery.getResultList();

        boolean hasNext = fetched.size() > criteria.size();
        List<ProductEntity> pageItems = hasNext ? fetched.subList(0, criteria.size()) : fetched;
        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            ProductEntity last = pageItems.get(pageItems.size() - 1);
            Product lastProduct = last.toDomain();
            String primaryValue = switch (criteria.sortOption()) {
                case LIKES -> String.valueOf(lastProduct.likeCount());
                case LATEST -> last.getCreatedAt().toString();
                case PRICE -> String.valueOf(lastProduct.price());
                case NAME -> lastProduct.name();
            };
            nextCursor = new ProductCursor(criteria.sortOption(), primaryValue, last.getId()).encode();
        }

        return new ProductCursorPage(
                pageItems.stream().map(ProductEntity::toDomain).toList(),
                criteria.size(),
                hasNext,
                nextCursor
        );
    }

    @Override
    public Page<Product> findAllIncludingDeleted(ProductListCriteria criteria) {
        return productJpaRepository.findAll(ProductSpecifications.from(criteria, true), criteria.toPageable())
                .map(ProductEntity::toDomain);
    }

    @Override
    public List<UUID> findIdsByBrandId(UUID brandId) {
        return productJpaRepository.findReferenceIdsByBrandReferenceIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public int updateLikeCount(UUID productId, long delta) {
        return productJpaRepository.updateLikeCount(productId, delta);
    }

    @Override
    public void softDeleteByBrandId(UUID brandId) {
        productJpaRepository.softDeleteByBrandReferenceId(brandId);
    }

    @Override
    public int decreaseStockAtomically(UUID productId, int quantity) {
        return productJpaRepository.decreaseStockAtomically(productId, quantity);
    }

    @Override
    public void delete(Product product) {
        productJpaRepository.findByReferenceId(product.id())
                .ifPresent(ProductEntity::delete);
    }
}
